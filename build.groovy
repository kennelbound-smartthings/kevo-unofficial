// Get the HTTP lib setup so we can get the build helper
@Grab('org.apache.httpcomponents:httpclient:4.2.1')
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
def httpClient = new DefaultHttpClient()

// Fetch and write locally the build helper
def url = 'https://raw.githubusercontent.com/kennelbound-smartthings/smartthings-build-helper/master/SmartthingsBuildHelper.groovy'
def httpGet = new HttpGet(url)
def httpResponse = httpClient.execute(httpGet)
new File('./SmartthingsBuildHelper.groovy').text = httpResponse.entity.content.text

// Load the now downloaded script into memory so we can use it
File sourceFile = new File("./SmartthingsBuildHelper.groovy")
Class SmartThingsBuildHelper = new GroovyClassLoader(getClass().getClassLoader()).parseClass(sourceFile)

// Method Signature for the compile is static void compile(main_file_name, String source_path, String output_path)
SmartThingsBuildHelper.compile('devicehandler', './src', 'devicetypes/kennelbound-smartthings/kevolock/kevo-unofficial.src/kevo-unofficial.groovy')

// Supports multiple outputs so you can build all the components of your project at the same time, with shared libs
SmartThingsBuildHelper.compile('smartapp', './src', 'smartapps/kennelbound-smartthings/kevolock/kevo-unofficial.src/kevo-unofficial.groovy')