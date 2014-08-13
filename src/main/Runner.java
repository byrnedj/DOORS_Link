package main;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.oauth.OAuthException;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.eclipse.lyo.client.oslc.OAuthRedirectException;
import org.eclipse.lyo.client.oslc.OSLCConstants;
import org.eclipse.lyo.client.oslc.OslcOAuthClient;
import org.scribe.builder.ServiceBuilder;
import org.scribe.oauth.OAuthService;

public class Runner {

	/**
	 * @param args
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws ClientProtocolException
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthNotAuthorizedException 
	 * @throws OAuthMessageSignerException 
	 */
	public static void main(String[] args) throws ParseException, ClientProtocolException, IOException, OAuthMessageSignerException, OAuthNotAuthorizedException, OAuthExpectationFailedException, OAuthCommunicationException 
	{
		Options options = new Options();
		
		options.addOption("url", true, "url");
		options.addOption("user", true, "user ID");
		options.addOption("password", true, "password");
		options.addOption("project",true,"project area");
		
		CommandLineParser cliParser = new GnuParser();			
		
		//Parse the command line
		CommandLine cmd = cliParser.parse(options, args);
		
		String webContextUrl = cmd.getOptionValue("url");
		String user = cmd.getOptionValue("user");
		String password = cmd.getOptionValue("password");
		String projectArea = cmd.getOptionValue("project");
		
		String requestTokenURL = "http://USNX47:8080/dwa/oauth-request-token";
		String authorizationTokenURL = "http://USNX47:8080/dwa/oauth-authorize-token";
		String accessTokenURL = "http://USNX47:8080/dwa/oauth-access-token";
		String consumerKey = "JIRA";
		String consumerSecret = consumerKey;
		
		//oauth using signpost
		
		//1. make a consumer and obtain request token
		OAuthConsumer doorsConsumer = new DefaultOAuthConsumer( consumerKey, consumerSecret );
		
		OAuthProvider doorsProvider = 
				new DefaultOAuthProvider( requestTokenURL, accessTokenURL, authorizationTokenURL );
		String authURL = doorsProvider.retrieveRequestToken( doorsConsumer, "oob");
		
		//2. authorize the request token
		HttpClient client = new DefaultHttpClient();
		HttpPost authPost = new HttpPost( authURL );
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("j_username", user));
		nvps.add(new BasicNameValuePair("j_password", password));
		authPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
		HttpResponse response = client.execute( authPost );
		
		//3. should get the access token from the provider now
		doorsProvider.retrieveAccessToken( doorsConsumer, "");
		
		//4. get magic reqs
		URL magicReqs = new URL("http://usnx47:8080/dwa/rm/urn:rational::1-4d2b67b464226e12-M-0000048a");
		HttpURLConnection request = (HttpURLConnection) magicReqs.openConnection();
		doorsConsumer.sign(request);
		request.connect();
	}
		
/*		//Step 1: oauth
		OslcOAuthClient client = new OslcOAuthClient(requestTokenURL, authorizationTokenURL, accessTokenURL, consumerKey, consumerSecret);
		try 
		{
			client.getResource(webContextUrl, OSLCConstants.CT_RDF);
		} 
		catch ( OAuthRedirectException oauthRedirect)
		{
			String redirectURL = oauthRedirect.getRedirectURL();
			String oauthReqToken = oauthRedirect.getAccessor().requestToken;
			String oauthTokenSecret = oauthRedirect.getAccessor().tokenSecret;
			
			
			//Do the POST
			HttpPost authPost = new HttpPost( "http://USNX47:8080/dwa/oauth/oauth_login.jsp" );
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("j_username", user));
			nvps.add(new BasicNameValuePair("j_password", password));
			authPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			
			HttpResponse authResponse = client.getHttpClient().execute(authPost);
			EntityUtils.consume( authResponse.getEntity());
			Header[] headers = authResponse.getAllHeaders();
			System.out.println( headers );
			
			// The server requires an authentication: Create the login form
			HttpPost formPost2 = new HttpPost("http://USNX47:8080/dwa/oauth/oauth_login.jsp");
			formPost2.getParams().setParameter("oauth_token", oauthReqToken );
			formPost2.getParams().setParameter("oauth_verifier", oauthTokenSecret);
			formPost2.getParams().setParameter("authorize", "true");
			formPost2.addHeader("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
			
			authResponse = client.getHttpClient().execute(formPost2);
			EntityUtils.consume(authResponse.getEntity());
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static Map<String, String> getQueryMap(String query) {
		Map<String, String> map = new HashMap<String, String>();
		String[] params = query.split("&"); //$NON-NLS-1$

		for (String param : params) {
		String name = param.split("=")[0]; //$NON-NLS-1$
		String value = param.split("=")[1]; //$NON-NLS-1$
		map.put(name, value);
		}

		return map;
	} */
	

}
