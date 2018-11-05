package ga.uuid.tinydownload.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * for download https resources <br>
 * unsafe
 */
public class TrustAnyHostnameVerifier implements HostnameVerifier {
	
	@Override
	public boolean verify(String hostname, SSLSession session) {
		return true;
	}
}