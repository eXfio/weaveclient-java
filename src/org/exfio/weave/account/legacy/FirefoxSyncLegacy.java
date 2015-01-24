package org.exfio.weave.account.legacy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.crypto.WeaveCryptoV5;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.StorageParams;
import org.exfio.weave.storage.StorageV1_1;
import org.exfio.weave.storage.StorageV1_1Params;
import org.exfio.weave.storage.WeaveCollectionInfo;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.URIUtils;

//FIXME - Add support for account management
public class FirefoxSyncLegacy extends WeaveAccount {
	
	public static final String KEY_ACCOUNT_CONFIG_SYNCKEY      = "synckey";

	private URI    baseURL;
	private String user;
	private String password;
	private String syncKey;
	private WeaveKeyPair keyPair;
	
	public FirefoxSyncLegacy() {
		super();
		this.version  = ApiVersion.v1_1;
		this.baseURL  = null;
		this.user     = null;
		this.password = null;
		this.syncKey  = null;
	}

	public void init(WeaveAccountParams params) throws WeaveException {
		FirefoxSyncLegacyParams initParams = (FirefoxSyncLegacyParams)params;
		this.init(initParams.accountServer, initParams.user, initParams.password, initParams.syncKey);		
	}
	
	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		this.user     = user;
		this.password = password;
		this.syncKey  = syncKey;
		
		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
	}

	@Override
	public void createAccount(WeaveAccountParams params) throws WeaveException {
		FirefoxSyncLegacyParams regParams = (FirefoxSyncLegacyParams)params;
		this.createAccount(regParams.accountServer, regParams.user, regParams.password, regParams.email);
	}

	@SuppressWarnings("unchecked")
	public void createAccount(String baseURL, String user, String password, String email) throws WeaveException {
		Log.getInstance().debug("createAccount()");
		
		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}

		//Build registration URL
		URI location = this.baseURL.resolve(URIUtils.sanitize(String.format("user/1.0/%s", user)));
				
		//Build HTTP request content
		JSONObject jobj = new JSONObject();
		jobj.put("password", password);
		jobj.put("email", email);
		
		//TODO - Support captcha
		jobj.put("captcha-challenge", "");
		jobj.put("captcha-response", "");
		
		HttpPut put = new HttpPut(location);
		CloseableHttpResponse response = null;

		try {
			//Backwards compatible with android version of org.apache.http
			StringEntity entityPut = new StringEntity(jobj.toJSONString());
			entityPut.setContentType("text/plain");
			entityPut.setContentEncoding("UTF-8");
			
			put.setEntity(entityPut);

			response = httpClient.execute(put);
			HttpClient.checkResponse(response);

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} finally {
			HttpClient.closeResponse(response);
		} 

		// Generate sync key
		SecureRandom rnd = new SecureRandom();
		byte[] syncKeyBin = rnd.generateSeed(16);
		
		Base32 b32codec = new Base32();
		String syncKeyB32 = b32codec.encodeToString(syncKeyBin);
		
		// Remove dash chars, convert to uppercase and translate L and O to 8 and 9
		String syncKey = syncKeyB32.toUpperCase()
									.replace('L', '8')
									.replace('O', '9')
									.replaceAll("=", "");
		        
		Log.getInstance().debug( String.format("generated sync key: %s", syncKey));

		init(baseURL, user, password, syncKey);

		//Initialise storage client with account details
		StorageContext storageClient = new StorageV1_1();
		storageClient.init(this);

		//Initialise server meta data
		WeaveCryptoV5 cryptoClient = new WeaveCryptoV5();
		cryptoClient.init(storageClient, this.getMasterKeyPair());
		cryptoClient.initServer();
	}

	@Override
	public String getStatus() {
		try {
			//Initialise storage client with account details
			StorageContext storageClient = new StorageV1_1();
			storageClient.init(this);
	
			//Initialise server meta data
			WeaveCryptoV5 cryptoClient = new WeaveCryptoV5();
			cryptoClient.init(storageClient, this.getMasterKeyPair());
			if ( cryptoClient.isInitialised() ) {
				String status = "";
				String seperator = "";
				Map<String, WeaveCollectionInfo> colInfo = storageClient.getInfoCollections();
				for (String collection: colInfo.keySet()) {
					WeaveCollectionInfo info = colInfo.get(collection);
					status += String.format("%s%s - items: %s, modified: %s", seperator, info.getName(), info.getCount(), info.getModified());
					seperator = "\n";
				}
				return status;
				
			} else {
				return "Weave Sync server not initialised";
			}
			
		} catch (WeaveException e) {
			return String.format("Error querying Weave Sync status - %s", e.getMessage());
		}
	}
	
	public WeaveAccountParams getAccountParams() {
		FirefoxSyncLegacyParams params = new FirefoxSyncLegacyParams();
		params.accountServer  = this.baseURL.toString();
		params.user           = this.user;
		params.password       = this.password;
		params.syncKey        = this.syncKey;
		return params;
	}

	public StorageParams getStorageParams() throws WeaveException {
 		StorageV1_1Params params = new StorageV1_1Params();
		params.storageURL     = this.getStorageUrl().toString();
		params.user           = this.user;
		params.password       = this.password;
		return params;
	}

	public void writeConfig(File configFile) throws IOException {
		Properties clientProp = new Properties();
		
		clientProp.setProperty(KEY_ACCOUNT_CONFIG_SERVER, this.baseURL.toString());
		clientProp.setProperty(KEY_ACCOUNT_CONFIG_USERNAME, this.user);
		clientProp.setProperty(KEY_ACCOUNT_CONFIG_SYNCKEY, this.syncKey);
		
		configFile.getParentFile().mkdirs();
		clientProp.store(new FileOutputStream(configFile), "");
	}

	public URI getStorageUrl() throws WeaveException {

		URI storageURL = null;
		
		//TODO - confirm account exists, i.e. /user/1.0/USER returns 1
		
		URI location = this.baseURL.resolve(URIUtils.sanitize(String.format("user/1.0/%s/node/weave", this.user)));
		HttpGet get = new HttpGet(location);
		CloseableHttpResponse response = null;

		try {
			response = httpClient.execute(get);
			HttpClient.checkResponse(response);
			
			storageURL = new URI(EntityUtils.toString(response.getEntity()));

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		} finally {
			HttpClient.closeResponse(response);
		}
		
		return storageURL;
	}
	
	public WeaveKeyPair getMasterKeyPair() throws WeaveException {
		Log.getInstance().debug("getMasterKeyPair()");
		
		if ( keyPair == null ) {
			
			// Derive key pair using SHA-256 HMAC-based HKDF of sync key
			// See https://docs.services.mozilla.com/sync/storageformat5.html#the-sync-key

			// Remove dash chars, convert to uppercase and translate 8 and 9 to L and O
			String syncKeyB32 = syncKey.toUpperCase()
										.replace('8', 'L')
										.replace('9', 'O')
										.replaceAll("-", "");

			Log.getInstance().debug( String.format("normalised sync key: %s",  syncKeyB32));

			// Pad base32 string to multiple of 8 chars (40 bits)
			if ( (syncKeyB32.length() % 8) > 0 ) {
				int paddedLength = syncKeyB32.length() + 8 - (syncKeyB32.length() % 8);
				syncKeyB32 = StringUtils.rightPad(syncKeyB32, paddedLength, '=');
			}

			Log.getInstance().debug( String.format("padded sync key: %s",  syncKeyB32));

			Base32 b32codec = new Base32();
			byte[] syncKeyBin = b32codec.decode(syncKeyB32);

			String keyInfo = "Sync-AES_256_CBC-HMAC256" + user;

			// For testing only
			//syncKeyBin = Hex.decodeHexString("c71aa7cbd8b82a8ff6eda55c39479fd2");
			//keyInfo = "Sync-AES_256_CBC-HMAC256" + "johndoe@example.com";

			Log.getInstance().debug( String.format("base32 key: %s decoded to %s", syncKey, Hex.encodeHexString(syncKeyBin)));

			keyPair = new WeaveKeyPair();

			try {
				byte[] message;
				Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
				hmacSHA256.init(new SecretKeySpec(syncKeyBin, "HmacSHA256"));
				
				message = ArrayUtils.addAll(keyInfo.getBytes(Constants.ASCII), new byte[]{1});
				keyPair.cryptKey = hmacSHA256.doFinal(message);
				message = ArrayUtils.addAll(ArrayUtils.addAll(keyPair.cryptKey, keyInfo.getBytes(Constants.ASCII)) , new byte[]{2});
				keyPair.hmacKey  = hmacSHA256.doFinal(message);
			} catch (NoSuchAlgorithmException e) {
				throw new WeaveException(String.format("Error deriving master keypair - %s", e.getMessage()));
			} catch (InvalidKeyException e) {
				throw new WeaveException(String.format("Error deriving master keypair - %s", e.getMessage()));
			}
			
			Log.getInstance().info( "Successfully generated sync key and hmac key");
			Log.getInstance().debug( String.format("sync key: %s, crypt key: %s, crypt hmac: %s", syncKey, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		}
		
		return keyPair;
	}
	
	public Properties accountParamsToProperties(WeaveAccountParams params) {
		FirefoxSyncLegacyParams fslParams = (FirefoxSyncLegacyParams)params;
		
		Properties prop = new Properties();
		prop.setProperty(KEY_ACCOUNT_CONFIG_APIVERSION, WeaveClientFactory.apiVersionToString(fslParams.getApiVersion()));
		prop.setProperty(KEY_ACCOUNT_CONFIG_SERVER,     fslParams.accountServer);
		prop.setProperty(KEY_ACCOUNT_CONFIG_USERNAME,   fslParams.user);
		prop.setProperty(KEY_ACCOUNT_CONFIG_SYNCKEY,    fslParams.syncKey);
		
		return prop;
	}

	public WeaveAccountParams propertiesToAccountParams(Properties prop) {
		FirefoxSyncLegacyParams fslParams = new FirefoxSyncLegacyParams();
		
		fslParams.accountServer = prop.getProperty(KEY_ACCOUNT_CONFIG_SERVER);
		fslParams.user          = prop.getProperty(KEY_ACCOUNT_CONFIG_USERNAME);
		fslParams.syncKey       = prop.getProperty(KEY_ACCOUNT_CONFIG_SYNCKEY);
		
		return fslParams;
	}

}
