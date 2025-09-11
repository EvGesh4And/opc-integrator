package ru.datana.integration.opc.component;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.security.KeyStore.getInstance;
import static java.util.Arrays.stream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.config.CertificateConfiguration;
import ru.datana.integration.opc.config.KeyPairEntryConfiguration;
import ru.datana.integration.opc.config.KeystoreConfiguration;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeyStoreLoader {
	private static final Pattern IP_ADDR_PATTERN = Pattern
			.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

	private final KeystoreConfiguration keystoreConfiguration;
	private final CertificateConfiguration certificateConfiguration;
	private final KeyPairEntryConfiguration keyPairEntryConfiguration;

	private X509Certificate[] clientCertificateChain;
	private X509Certificate clientCertificate;
	private KeyPair clientKeyPair;

	public KeyStoreLoader load(Path baseDir) throws Exception {
		var keyStore = getInstance(keystoreConfiguration.algo());

		var serverKeyStore = baseDir.resolve(keystoreConfiguration.filename());

		log.info("Loading KeyStore at {}", serverKeyStore);

		var password = keystoreConfiguration.password().toCharArray();
		var clientAlias = certificateConfiguration.clientAlias();
		if (!exists(serverKeyStore)) {
			keyStore.load(null, password);

			var keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(keyPairEntryConfiguration.length());

			var builder = new SelfSignedCertificateBuilder(keyPair).setCommonName(certificateConfiguration.commonName())
					.setOrganization(certificateConfiguration.organization())
					.setOrganizationalUnit(certificateConfiguration.orgUnit())
					.setLocalityName(certificateConfiguration.locality()).setStateName(certificateConfiguration.state())
					.setCountryCode(certificateConfiguration.countryCode())
					.setApplicationUri(certificateConfiguration.applicationUrl())
					.addDnsName(certificateConfiguration.dnsName()).addIpAddress(certificateConfiguration.ipAddress());

			// Get as many hostnames and IP addresses as we can listed in the certificate.
			for (String hostname : HostnameUtil.getHostnames("0.0.0.0")) {
				if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
					builder.addIpAddress(hostname);
				} else {
					builder.addDnsName(hostname);
				}
			}

			var certificate = builder.build();

			keyStore.setKeyEntry(clientAlias, keyPair.getPrivate(), password, new X509Certificate[] { certificate });
			try (OutputStream out = newOutputStream(serverKeyStore)) {
				keyStore.store(out, password);
			}
		} else {
			try (InputStream in = newInputStream(serverKeyStore)) {
				keyStore.load(in, password);
			}
		}

		var clientPrivateKey = keyStore.getKey(clientAlias, password);
		if (clientPrivateKey instanceof PrivateKey privateKey) {
			clientCertificate = (X509Certificate) keyStore.getCertificate(clientAlias);

			clientCertificateChain = stream(keyStore.getCertificateChain(clientAlias)).map(X509Certificate.class::cast)
					.toArray(X509Certificate[]::new);

			var serverPublicKey = clientCertificate.getPublicKey();
			clientKeyPair = new KeyPair(serverPublicKey, privateKey);
		}

		return this;
	}

	public X509Certificate getClientCertificate() {
		return clientCertificate;
	}

	public X509Certificate[] getClientCertificateChain() {
		return clientCertificateChain;
	}

	public KeyPair getClientKeyPair() {
		return clientKeyPair;
	}
}
