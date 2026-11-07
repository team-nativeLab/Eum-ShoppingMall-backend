import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * RDS CA 묶음(PEM)의 인증서를 모두 PKCS12 신뢰 저장소에 넣는다(#133). 이미지를 만들 때 Dockerfile에서 한 번 실행한다.
 * keytool은 PEM 묶음에서 첫 인증서만 넣고 인증서마다 JVM을 새로 띄워야 해서, JVM 한 번으로 모두 넣으려고 따로 둔다.
 *
 * 실행: java BuildTruststore.java <PEM 묶음> <만들 .p12> <저장소 비밀번호>
 */
public class BuildTruststore {

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			throw new IllegalArgumentException("usage: java BuildTruststore.java <bundle.pem> <truststore.p12> <password>");
		}
		Collection<? extends Certificate> certificates;
		try (InputStream in = new FileInputStream(args[0])) {
			certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
		}
		if (certificates.isEmpty()) {
			throw new IllegalStateException("인증서가 없습니다: " + args[0]);
		}
		char[] password = args[2].toCharArray();
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, password);
		int index = 0;
		for (Certificate certificate : certificates) {
			X509Certificate x509 = (X509Certificate) certificate;
			if (!x509.getSubjectX500Principal().getName().contains("Amazon RDS")) {
				throw new IllegalStateException("RDS CA가 아닌 인증서가 있습니다: " + x509.getSubjectX500Principal());
			}
			store.setCertificateEntry("rds-" + (++index), certificate);
		}
		try (OutputStream out = new FileOutputStream(args[1])) {
			store.store(out, password);
		}
		System.out.println("RDS CA " + index + "개를 신뢰 저장소에 넣었습니다: " + args[1]);
	}
}
