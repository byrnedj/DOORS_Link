/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *     Gabriel Ruelas     - initial API and implementation
 *******************************************************************************/
package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.oauth.OAuthException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.wink.client.ClientResponse;
import org.eclipse.lyo.client.exception.ResourceNotFoundException;
import org.eclipse.lyo.client.exception.RootServicesException;
import org.eclipse.lyo.client.oslc.OAuthRedirectException;
import org.eclipse.lyo.client.oslc.OSLCConstants;
import org.eclipse.lyo.client.oslc.OslcClient;
import org.eclipse.lyo.client.oslc.OslcOAuthClient;
import org.eclipse.lyo.client.oslc.jazz.JazzRootServicesHelper;
import org.eclipse.lyo.client.oslc.resources.OslcQuery;
import org.eclipse.lyo.client.oslc.resources.OslcQueryParameters;
import org.eclipse.lyo.client.oslc.resources.OslcQueryResult;
import org.eclipse.lyo.client.oslc.resources.Requirement;
import org.eclipse.lyo.client.oslc.resources.RequirementCollection;
import org.eclipse.lyo.client.oslc.resources.RmConstants;
import org.eclipse.lyo.client.oslc.resources.RmUtil;
import org.eclipse.lyo.oslc4j.core.model.CreationFactory;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;
import org.eclipse.lyo.oslc4j.core.model.ResourceShape;
import org.eclipse.lyo.oslc4j.core.model.Service;
import org.eclipse.lyo.oslc4j.core.model.ServiceProvider;
import org.eclipse.lyo.oslc4j.provider.jena.OslcRdfXmlCollectionProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ReifiedStatement;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * Samples of logging in to Doors Web Access and running OSLC operations
 * 
 * 
 * - run an OLSC Requirement query and retrieve OSLC Requirements and de-serialize them as Java objects
 * - TODO:  Add more requirement sample scenarios
 *
 */
public class DOORS_Service {

	private static final Logger logger = Logger.getLogger(DOORS_Service.class.getName());

	/**
	 * Login to the DWA server and perform some OSLC actions
	 * @param args
	 * @throws ParseException 
	 */
	public static void main(String[] args) throws ParseException {

		Options options = new Options();

		options.addOption("url", true, "url");
		options.addOption("user", true, "user ID");
		options.addOption("password", true, "password");
		options.addOption("project",true,"project area");

		CommandLineParser cliParser = new GnuParser();			

		//Parse the command line
		CommandLine cmd = cliParser.parse(options, args);

		if (!validateOptions(cmd)) 
		{		
			logger.severe("Syntax:  java <class_name> -url https://<server>:port/<context>/ -user <user> -password <password> -project \"<project_area>\"");
			logger.severe("Example: java DoorsOauthSample -url https://exmple.com:9443/dwa -user ADMIN -password ADMIN -project \"JKE Banking (Requirements Management)\"");
			return;
		}

		String webContextUrl = cmd.getOptionValue("url");
		String user = cmd.getOptionValue("user");
		String passwd = cmd.getOptionValue("password");
		String projectArea = cmd.getOptionValue("project");

		try 
		{
			//STEP 1: Initialize a Jazz rootservices helper and indicate we're looking for the RequirementManagement catalog
			// The root services for DOORs is found at /public level
			JazzRootServicesHelper helper = new JazzRootServicesHelper(webContextUrl + "/public",OSLCConstants.OSLC_RM);

			//STEP 2: Create a new OSLC OAuth capable client
			OslcOAuthClient client = helper.initOAuthClient("JIRA", "JIRA");

			if ( client != null ) 
			{
				//STEP 3: Try to access the context URL to trigger the OAuth dance and login
				try 
				{
					client.getResource(webContextUrl,OSLCConstants.CT_RDF);
				} 
				catch (OAuthRedirectException oauthE) 
				{
					validateTokens(client,  oauthE.getRedirectURL() + "?oauth_token=" + oauthE.getAccessor().requestToken, user, passwd, webContextUrl + "/j_acegi_security_check" );
					// Try to access again
					ClientResponse response = client.getResource( webContextUrl,OSLCConstants.CT_RDF );
					response.getEntity( InputStream.class ).close();
				}

				//STEP 4: Get our requirements collection that we want
				//TODO: Replace with option from startup
				String serviceProviderUrl = "http://usnx47:8080/dwa/rm/urn:rational::1-4d2b67b464226e12-M-0000048a";
				ClientResponse response = client.getResource(serviceProviderUrl, "application/x-oslc-rm-requirement-collection-1.0+xml" );
				//build the rdf
				Model rdfModel = ModelFactory.createDefaultModel();
				rdfModel.read( response.getEntity( InputStream.class ) , serviceProviderUrl );
				response.consumeContent();

				//get the statements
				List<Statement> reqs = rdfModel.getResource(serviceProviderUrl).listProperties().toList();
				HashMap<String,String> requirements = new HashMap<String,String>();
				for ( Statement s : reqs )
				{


					String reqURI = s.getObject().toString();
					if (reqURI.contains("http"))
					{
						response = client.getResource( reqURI, "application/x-oslc-rm-requirement-1.0+xml" );
						if ( response.getStatusCode() == 200 )
						{
							InputStream in = response.getEntity( InputStream.class );
							Model model = ModelFactory.createDefaultModel();

							try
							{
								model.read( in, reqURI );
							}
							catch (Exception sa )
							{
								System.out.println(reqURI);
							}

							//Properties to traverse on
							Property attrDef = model.createProperty("http://jazz.net/doors/xmlns/prod/jazz/doors/1.0/attrDef");
							Property name = model.createProperty("http://jazz.net/doors/xmlns/prod/jazz/doors/1.0/name");

							//Flags we use for parsing
							int count = 0;
							boolean isText = false;
							boolean isID = false;
							boolean done = false;
							//Text of the DOORS Object and its ID are what we are going to extract
							String text = "";
							String id = "";

							//Look through all of the possible fields
							StmtIterator statementIter = model.listStatements();
							while ( statementIter.hasNext() && done != true )
							{
								Statement field = statementIter.next();
								//Get the attrDef property to find out what kind of value we have
								StmtIterator props = field.getSubject().listProperties( attrDef );
								while ( props.hasNext() && done != true )
								{
									Statement kind = props.next();
									RDFNode propertyNode = kind.getObject();
									StmtIterator propIt = propertyNode.asResource().listProperties( name );
									//Check all of the properties for our desired fields
									while ( propIt.hasNext() )
									{
										Statement node = propIt.next();
										if ( node.getObject().isLiteral() )
										{
											if (node.getObject().toString().contains("Object+Text") && field.getObject().isLiteral() )
											{
												text = field.getLiteral().toString();
												text = text.substring( 0, text.indexOf("^") );
												count++;
												
											}
											if ( node.getObject().toString().contains("Absolute+Number") && field.getObject().isLiteral() )
											{
												id =  field.getLiteral().toString();
												id = id.substring( 0, id.indexOf( "^") );
												count++;	
											}

										}
									}
									if ( count == 2 )
									{
										if ( !text.isEmpty() )
										{
											//System.out.println( "Req: " + id );
											//System.out.println( text );
											requirements.put( id, text );
											count = 0;
											done = true;
											break;
										}
										
									}
								}

							}

						}

					}
					response.consumeContent();
				}
				//check if already in JIRA
				//post to jira
				for ( Entry<String, String> e : requirements.entrySet() ) 
				{
					
				}
			}		

		} 
		catch (Exception e) 
		{
			logger.log(Level.SEVERE,e.getMessage(),e);
		}


	}


	private static boolean validateOptions(CommandLine cmd) 
	{
		boolean isValid = true;

		if (! (cmd.hasOption("url") &&
				cmd.hasOption("user") &&
				cmd.hasOption("password") &&
				cmd.hasOption("project"))) {

			isValid = false;
		}
		return isValid;		
	}

	/**
	 * Print out the HTTPResponse headers
	 */
	public static void printResponseHeaders(HttpResponse response) {
		Header[] headers = response.getAllHeaders();
		for (int i = 0; i < headers.length; i++) {
			System.out.println("\t- " + headers[i].getName() + ": " + headers[i].getValue());
		}
	}

	public static Map<String, String> getQueryMap(String query) {
		Map<String, String> map = new HashMap<String, String>();
		query = query.substring( query.indexOf( '?') + 1 );
		String[] params = query.split("&"); //$NON-NLS-1$

		for (String param : params) {
			String name = param.split("=")[0]; //$NON-NLS-1$
			String value = param.split("=")[1]; //$NON-NLS-1$
			map.put(name, value);
		}

		return map;
	} 

	private static void validateTokens(OslcOAuthClient client, String redirect, String user, String password, String authURL) throws Exception {

		HttpGet request2 = new HttpGet(redirect);
		HttpClientParams.setRedirecting(request2.getParams(), false);
		HttpResponse response = client.getHttpClient().execute(request2);
		EntityUtils.consume(response.getEntity());

		// Get the location
		Header location = response.getFirstHeader("Location");
		HttpGet request3 = new HttpGet(location.getValue());
		HttpClientParams.setRedirecting(request3.getParams(), false);	
		response = client.getHttpClient().execute(request3);
		EntityUtils.consume(response.getEntity());

		//POST to login form
		// The server requires an authentication: Create the login form
		// Following line should be like : "https://server:port/dwa/j_acegi_security_check"
		HttpPost formPost = new HttpPost(authURL);
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("j_username", user));
		nvps.add(new BasicNameValuePair("j_password", password));
		formPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

		HttpResponse formResponse = client.getHttpClient().execute(formPost);
		EntityUtils.consume(formResponse.getEntity());

		location = formResponse.getFirstHeader("Location");
		//Third GET
		HttpGet request4 = new HttpGet(location.getValue());
		HttpClientParams.setRedirecting(request4.getParams(), false);
		response = client.getHttpClient().execute(request4);
		EntityUtils.consume(response.getEntity());

		location = response.getFirstHeader("Location");
		Map<String,String> oAuthMap = getQueryMap(location.getValue());
		String oauthToken = oAuthMap.get("oauth_token");
		String oauthverifier = oAuthMap.get("oauth_verifier");

		// The server requires an authentication: Create the login form
		HttpPost formPost2 = new HttpPost(location.getValue());
		formPost2.addHeader("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
		HttpParams params = new BasicHttpParams();
		params.setParameter("oauth_token", oauthToken);
		params.setParameter("oauth_verifier", oauthverifier);
		params.setParameter("authorize", "true");
		formPost2.setParams(params);

		formResponse = client.getHttpClient().execute(formPost2);
		EntityUtils.consume(formResponse.getEntity());

		Header header = formResponse.getFirstHeader("Content-Length");
		if ((header!=null) && (!("0".equals(header.getValue())))) {
			// The login failed
			throw new InvalidCredentialsException("Authentication failed");
		} else {
			// The login succeed
			// Step (3): Request again the protected resource
			EntityUtils.consume(formResponse.getEntity());
		}
	}

	/**
	 * Lookup the URL of a specific OSLC Service Provider in an OSLC Catalog using the service provider's title
	 * 
	 * @param catalogUrl
	 * @param serviceProviderTitle
	 * @return
	 * @throws IOException
	 * @throws OAuthException
	 * @throws URISyntaxException
	 * @throws ResourceNotFoundException 
	 */
	public static String lookupServiceProviderUrl(final String catalogUrl, final String serviceProviderTitle, final OslcOAuthClient client) 
			throws IOException, OAuthException, URISyntaxException, ResourceNotFoundException
			{
		String retval = null;
		ClientResponse response = client.getResource(catalogUrl,OSLCConstants.CT_RDF);
		Model rdfModel = ModelFactory.createDefaultModel();

		rdfModel.read(response.getEntity(InputStream.class),catalogUrl);

		// Step 1 Check if it is the service provider we are looking for by comparing the name		
		ResIterator listResources = rdfModel.listResourcesWithProperty(RDF.type,rdfModel.createResource("http://open-services.net/ns/core#ServiceProvider"));
		Property titleProp = rdfModel.createProperty(OSLCConstants.DC,"title");
		//check each serviceProvider's title and match it to the one passed in
		while (listResources.hasNext()) {
			Resource resource = listResources.next();
			Statement titlestatement = resource.getProperty(titleProp);
			if (titlestatement == null)
				continue;
			String mytitle = titlestatement.getLiteral().getString();
			System.out.println(mytitle);
			if (( mytitle != null) && (mytitle.equalsIgnoreCase(serviceProviderTitle))) {
				System.out.println("Project Found");
				retval =  catalogUrl;
			}
		}

		// Step 2 Check if there are Service providers properties to recursively look in them
		if ( retval == null) {
			Property spPredicate = rdfModel.createProperty(OSLCConstants.OSLC_V2,"serviceProvider");
			Selector select = new SimpleSelector(null, spPredicate, (RDFNode)null); 
			StmtIterator listStatements = rdfModel.listStatements(select);

			//check each serviceProvider's title and match it to the one passed in
			while (listStatements.hasNext()) {
				Statement thisSP = listStatements.nextStatement();
				com.hp.hpl.jena.rdf.model.Resource spRes = thisSP.getResource();
				if ( spRes.isResource()) {
					// Recursively look for the project Name
					String newURL = spRes.getURI();
					try {
						return lookupServiceProviderUrl(newURL, serviceProviderTitle, client);
					} catch (ResourceNotFoundException nf){

					}
				}
			}

		}

		// Step 3 Check if there are ServiceProvider catalog and recursively look in them
		if ( retval == null) {
			Property spcPredicate = rdfModel.createProperty(OSLCConstants.OSLC_V2,"serviceProviderCatalog");
			Selector select = new SimpleSelector(null, spcPredicate, (RDFNode)null); 
			StmtIterator listStatements = rdfModel.listStatements(select);

			//check each serviceProvider's title and match it to the one passed in
			while (listStatements.hasNext()) {
				Statement thisSP = listStatements.nextStatement();
				com.hp.hpl.jena.rdf.model.Resource spRes = thisSP.getResource();
				if ( spRes.isResource()) {
					// Recursively look for the project Name
					String newURL = spRes.getURI();
					try {
						return lookupServiceProviderUrl(newURL, serviceProviderTitle, client);
					} catch (ResourceNotFoundException nf){

					}
				} 
			}
		}

		if (retval == null ) {
			throw new ResourceNotFoundException(catalogUrl, serviceProviderTitle);
		}

		return retval;
			}


	/**
	 * Find the OSLC Instance Shape URL for a given OSLC resource type.  
	 *
	 * @param serviceProviderUrl
	 * @param oslcDomain
	 * @param oslcResourceType - the resource type of the desired query capability.   This may differ from the OSLC artifact type.
	 * @return URL of requested Creation Factory or null if not found.
	 * @throws IOException
	 * @throws OAuthException
	 * @throws URISyntaxException
	 * @throws ResourceNotFoundException 
	 */
	public static ResourceShape lookupRequirementsInstanceShapesOLD(final String serviceProviderUrl, final String oslcDomain, final String oslcResourceType, OslcOAuthClient client, String requiredInstanceShape) 
			throws IOException, OAuthException, URISyntaxException, ResourceNotFoundException
			{

		ClientResponse response = client.getResource(serviceProviderUrl,OSLCConstants.CT_RDF);
		ServiceProvider serviceProvider = response.getEntity(ServiceProvider.class);

		if (serviceProvider != null) {
			for (Service service:serviceProvider.getServices()) {
				URI domain = service.getDomain();				
				if (domain != null  && domain.toString().equals(oslcDomain)) {
					CreationFactory [] creationFactories = service.getCreationFactories();
					if (creationFactories != null && creationFactories.length > 0) {
						for (CreationFactory creationFactory:creationFactories) {
							for (URI resourceType:creationFactory.getResourceTypes()) {

								//return as soon as domain + resource type are matched
								if (resourceType.toString() != null && resourceType.toString().equals(oslcResourceType)) {
									URI[] instanceShapes = creationFactory.getResourceShapes();
									if (instanceShapes != null ){
										for ( URI typeURI : instanceShapes) {
											response = client.getResource(typeURI.toString(),OSLCConstants.CT_RDF);
											ResourceShape resourceShape =  response.getEntity(ResourceShape.class);
											String typeTitle = resourceShape.getTitle();
											if ( ( typeTitle != null) && (typeTitle.equalsIgnoreCase(requiredInstanceShape)) ) {
												return resourceShape;	
											}
										}
									}
								}							
							}
						}
					}
				}
			}
		}


		throw new ResourceNotFoundException(serviceProviderUrl, "InstanceShapes");
			}


	/**
	 * Remove XML Escape indicators
	 * 
	 * @param content
	 * @return String
	 */
	public static String removeXMLEscape(String content) {
		content = content.replaceAll("&lt;", "<"); //$NON-NLS-1$ //$NON-NLS-2$
		content = content.replaceAll("&gt;", ">"); //$NON-NLS-1$ //$NON-NLS-2$
		content = content.replaceAll("&quot;", "\""); //$NON-NLS-1$ //$NON-NLS-2$
		content = content.replaceAll("&#039;", "\'"); //$NON-NLS-1$ //$NON-NLS-2$
		content = content.replaceAll("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$
		content = content.trim();
		return content;
	}

}
