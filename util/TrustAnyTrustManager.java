package ga.uuid.app.util;

import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * for download https resources <br>
 * unsafe
 */
public class TrustAnyTrustManager implements X509TrustManager {
	
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[]{};
	}
}