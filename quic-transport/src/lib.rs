use std::{
    collections::HashMap,
    net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr},
    sync::{atomic::{AtomicI32, Ordering}, Arc, Mutex},
    time::Duration,
};

use bytes::Bytes;
use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jboolean, jbyteArray, jint, jlong, jstring},
    JNIEnv,
};
use quinn::{Connection, Endpoint, RecvStream, SendStream};
use rustls::{
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    crypto::{verify_tls12_signature, verify_tls13_signature, WebPkiSupportedAlgorithms},
    pki_types::{CertificateDer, ServerName, UnixTime},
    DigitallySignedStruct, Error as TlsError, SignatureScheme,
};
use sha2::{Digest, Sha256};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    runtime::{Builder, Runtime},
    sync::Mutex as AsyncMutex,
    time::timeout,
};

const ALPN: &[u8] = b"syzygy-quic-1";
const MAX_AUTH_LINE: usize = 32 * 1024;

#[derive(Debug)]
struct PinnedVerifier {
    pin: [u8; 32],
    algorithms: WebPkiSupportedAlgorithms,
}

impl ServerCertVerifier for PinnedVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, TlsError> {
        let actual = Sha256::digest(end_entity.as_ref());
        if actual.as_slice() != self.pin {
            return Err(TlsError::General("Syzygy QUIC host certificate pin mismatch".into()));
        }
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, TlsError> {
        verify_tls12_signature(message, cert, dss, &self.algorithms)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, TlsError> {
        verify_tls13_signature(message, cert, dss, &self.algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        self.algorithms.supported_schemes()
    }
}

struct BidiStream {
    send: AsyncMutex<SendStream>,
    recv: AsyncMutex<RecvStream>,
}

struct Client {
    runtime: Runtime,
    _endpoint: Endpoint,
    connection: Connection,
    streams: Mutex<HashMap<i32, Arc<BidiStream>>>,
    next_stream: AtomicI32,
}

fn parse_pin(value: &str) -> Result<[u8; 32], String> {
    if value.len() != 64 {
        return Err("QUIC certificate pin must contain 64 hexadecimal characters".into());
    }
    let mut pin = [0u8; 32];
    for (index, pair) in value.as_bytes().chunks_exact(2).enumerate() {
        let high = (pair[0] as char).to_digit(16).ok_or("Invalid certificate pin")?;
        let low = (pair[1] as char).to_digit(16).ok_or("Invalid certificate pin")?;
        pin[index] = ((high << 4) | low) as u8;
    }
    Ok(pin)
}

fn make_client_config(pin: [u8; 32]) -> Result<quinn::ClientConfig, String> {
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let algorithms = provider.signature_verification_algorithms;
    let mut tls = rustls::ClientConfig::builder_with_provider(provider)
        .with_safe_default_protocol_versions()
        .map_err(|e| e.to_string())?
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(PinnedVerifier { pin, algorithms }))
        .with_no_client_auth();
    tls.alpn_protocols = vec![ALPN.to_vec()];

    let crypto = quinn::crypto::rustls::QuicClientConfig::try_from(tls)
        .map_err(|e| e.to_string())?;
    let mut config = quinn::ClientConfig::new(Arc::new(crypto));
    let mut transport = quinn::TransportConfig::default();
    transport.max_concurrent_bidi_streams(16u32.into());
    transport.datagram_receive_buffer_size(Some(256 * 1024));
    transport.datagram_send_buffer_size(256 * 1024);
    transport.keep_alive_interval(Some(Duration::from_secs(5)));
    config.transport_config(Arc::new(transport));
    Ok(config)
}

async fn read_line(stream: &mut RecvStream) -> Result<String, String> {
    let mut line = Vec::with_capacity(256);
    loop {
        let mut byte = [0u8; 1];
        let count = stream.read(&mut byte).await.map_err(|e| e.to_string())?;
        if count == 0 {
            return Err("QUIC authentication stream closed before newline".into());
        }
        if byte[0] == b'\n' {
            return String::from_utf8(line).map_err(|e| e.to_string());
        }
        if line.len() >= MAX_AUTH_LINE {
            return Err("QUIC authentication line exceeds size limit".into());
        }
        line.push(byte[0]);
    }
}

fn client_from_handle<'a>(handle: jlong) -> Result<&'a Client, String> {
    if handle == 0 {
        return Err("Invalid QUIC client handle".into());
    }
    // The Java owner must call nativeClose exactly once after all forwarding threads stop.
    Ok(unsafe { &*(handle as *const Client) })
}

fn throw_error(env: &mut JNIEnv, message: impl ToString) {
    let _ = env.throw_new("java/lang/IllegalStateException", message.to_string());
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeConnect(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    port: jint,
    pin: JString,
) -> jlong {
    let result = (|| -> Result<jlong, String> {
        let host: String = env.get_string(&host).map_err(|e| e.to_string())?.into();
        let pin_text: String = env.get_string(&pin).map_err(|e| e.to_string())?.into();
        if port <= 0 || port > 65535 {
            return Err("Invalid Syzygy QUIC port".into());
        }
        let pin = parse_pin(&pin_text)?;
        let address = format!("{host}:{port}").parse::<SocketAddr>()
            .map_err(|e| format!("Invalid QUIC host address: {e}"))?;
        let runtime = Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|e| e.to_string())?;
        let config = make_client_config(pin)?;
        let (endpoint, connection, auth) = runtime.block_on(async {
            let bind_address = if address.is_ipv6() {
                SocketAddr::new(IpAddr::V6(Ipv6Addr::UNSPECIFIED), 0)
            } else {
                SocketAddr::new(IpAddr::V4(Ipv4Addr::UNSPECIFIED), 0)
            };
            let mut endpoint = Endpoint::client(bind_address).map_err(|e| e.to_string())?;
            endpoint.set_default_client_config(config);
            let connecting = endpoint.connect(address, "syzygy").map_err(|e| e.to_string())?;
            let connection = timeout(Duration::from_secs(8), connecting).await
                .map_err(|_| "Timed out connecting to Syzygy QUIC relay")?
                .map_err(|e| e.to_string())?;
            let (mut send, recv) = connection.open_bi().await.map_err(|e| e.to_string())?;
            send.write_all(b"{\"hello\":1}\n").await.map_err(|e| e.to_string())?;
            Ok::<_, String>((endpoint, connection, Arc::new(BidiStream {
                send: AsyncMutex::new(send),
                recv: AsyncMutex::new(recv),
            })))
        })?;
        let mut streams = HashMap::new();
        streams.insert(0, auth);
        let client = Box::new(Client {
            runtime,
            _endpoint: endpoint,
            connection,
            streams: Mutex::new(streams),
            next_stream: AtomicI32::new(1),
        });
        Ok(Box::into_raw(client) as jlong)
    })();
    match result {
        Ok(handle) => handle,
        Err(error) => { throw_error(&mut env, error); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeReadAuthChallenge(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let client = client_from_handle(handle)?;
        let stream = client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .get(&0).cloned().ok_or("Missing QUIC authentication stream")?;
        client.runtime.block_on(async {
            timeout(Duration::from_secs(5), async {
                let mut recv = stream.recv.lock().await;
                read_line(&mut recv).await
            }).await.map_err(|_| "Timed out waiting for Syzygy QUIC challenge")?
        })
    })();
    match result {
        Ok(line) => env.new_string(line).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(error) => { throw_error(&mut env, error); std::ptr::null_mut() }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeAuthenticate(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    proof: JString,
) -> jboolean {
    let result = (|| -> Result<bool, String> {
        let proof: String = env.get_string(&proof).map_err(|e| e.to_string())?.into();
        if proof.len() > MAX_AUTH_LINE || !proof.ends_with('\n') {
            return Err("Invalid Syzygy QUIC proof line".into());
        }
        let client = client_from_handle(handle)?;
        let stream = client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .get(&0).cloned().ok_or("Missing QUIC authentication stream")?;
        client.runtime.block_on(async {
            timeout(Duration::from_secs(5), async {
                let mut send = stream.send.lock().await;
                send.write_all(proof.as_bytes()).await.map_err(|e| e.to_string())?;
                let mut recv = stream.recv.lock().await;
                read_line(&mut recv).await
            }).await.map_err(|_| "Timed out waiting for Syzygy authorization")?
        }).and_then(|line| {
            let value: serde_json::Value = serde_json::from_str(&line).map_err(|e| e.to_string())?;
            Ok(value.as_object().map(|object| object.len() == 1 &&
                object.get("authorized") == Some(&serde_json::Value::Bool(true))).unwrap_or(false))
        })
    })();
    match result {
        Ok(true) => 1,
        Ok(false) => 0,
        Err(error) => { throw_error(&mut env, error); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeOpenBi(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    preface: JString,
) -> jint {
    let result = (|| -> Result<jint, String> {
        let preface: String = env.get_string(&preface).map_err(|e| e.to_string())?.into();
        if preface.len() > MAX_AUTH_LINE || !preface.ends_with('\n') {
            return Err("Invalid QUIC stream preface".into());
        }
        let client = client_from_handle(handle)?;
        let (mut send, recv) = client.runtime.block_on(client.connection.open_bi())
            .map_err(|e| e.to_string())?;
        client.runtime.block_on(send.write_all(preface.as_bytes()))
            .map_err(|e| e.to_string())?;
        let id = client.next_stream.fetch_add(1, Ordering::Relaxed);
        client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .insert(id, Arc::new(BidiStream { send: AsyncMutex::new(send), recv: AsyncMutex::new(recv) }));
        Ok(id)
    })();
    match result {
        Ok(id) => id,
        Err(error) => { throw_error(&mut env, error); -1 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeStreamWrite(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    stream_id: jint,
    data: JByteArray,
) -> jboolean {
    let result = (|| -> Result<(), String> {
        let data = env.convert_byte_array(data).map_err(|e| e.to_string())?;
        let client = client_from_handle(handle)?;
        let stream = client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .get(&stream_id).cloned().ok_or("Unknown QUIC stream")?;
        client.runtime.block_on(async {
            let mut send = stream.send.lock().await;
            send.write_all(&data).await.map_err(|e| e.to_string())
        })
    })();
    match result {
        Ok(()) => 1,
        Err(error) => { throw_error(&mut env, error); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeStreamRead(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    stream_id: jint,
    max_bytes: jint,
    timeout_ms: jint,
) -> jbyteArray {
    let result = (|| -> Result<Option<Vec<u8>>, String> {
        if max_bytes <= 0 || max_bytes > 65536 {
            return Err("Invalid QUIC stream read size".into());
        }
        let client = client_from_handle(handle)?;
        let stream = client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .get(&stream_id).cloned().ok_or("Unknown QUIC stream")?;
        client.runtime.block_on(async {
            let mut recv = stream.recv.lock().await;
            let mut buffer = vec![0u8; max_bytes as usize];
            match timeout(Duration::from_millis(timeout_ms.max(1) as u64), recv.read(&mut buffer)).await {
                Err(_) => Ok(None),
                Ok(Err(error)) => Err(error.to_string()),
                Ok(Ok(count)) => { buffer.truncate(count); Ok(Some(buffer)) }
            }
        })
    })();
    match result {
        Ok(Some(bytes)) => env.byte_array_from_slice(&bytes).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
        Ok(None) => std::ptr::null_mut(),
        Err(error) => { throw_error(&mut env, error); std::ptr::null_mut() }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeStreamFinish(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    stream_id: jint,
) -> jboolean {
    let result = (|| -> Result<(), String> {
        let client = client_from_handle(handle)?;
        let stream = client.streams.lock().map_err(|_| "QUIC stream map poisoned")?
            .get(&stream_id).cloned().ok_or("Unknown QUIC stream")?;
        client.runtime.block_on(async {
            let mut send = stream.send.lock().await;
            send.finish().map_err(|e| e.to_string())
        })
    })();
    match result {
        Ok(()) => 1,
        Err(error) => { throw_error(&mut env, error); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeSendDatagram(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    data: JByteArray,
) -> jboolean {
    let result = (|| -> Result<(), String> {
        let data = env.convert_byte_array(data).map_err(|e| e.to_string())?;
        let client = client_from_handle(handle)?;
        client.connection.send_datagram(Bytes::from(data)).map_err(|e| e.to_string())
    })();
    match result {
        Ok(()) => 1,
        Err(error) => { throw_error(&mut env, error); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeReceiveDatagram(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    timeout_ms: jint,
) -> jbyteArray {
    let result = (|| -> Result<Option<Vec<u8>>, String> {
        let client = client_from_handle(handle)?;
        client.runtime.block_on(async {
            match timeout(Duration::from_millis(timeout_ms.max(1) as u64), client.connection.read_datagram()).await {
                Err(_) => Ok(None),
                Ok(Err(error)) => Err(error.to_string()),
                Ok(Ok(bytes)) => Ok(Some(bytes.to_vec())),
            }
        })
    })();
    match result {
        Ok(Some(bytes)) => env.byte_array_from_slice(&bytes).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
        Ok(None) => std::ptr::null_mut(),
        Err(error) => { throw_error(&mut env, error); std::ptr::null_mut() }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_limelight_nvstream_quic_SyzygyQuicNative_nativeClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        let client = unsafe { Box::from_raw(handle as *mut Client) };
        client.connection.close(0u32.into(), b"client closed");
        drop(client);
    }
}
