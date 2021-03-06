package grails.util

import org.codehaus.groovy.grails.plugins.GrailsPlugin
import org.codehaus.groovy.grails.plugins.GrailsPluginManager
import org.springframework.core.io.Resource

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class PluginBuildSettingsTests extends GroovyTestCase {

    private static final File TEST_PROJ_DIR = new File("test/test-projects/plugin-build-settings")
    private static final File NESTED_INLINE_PLUGIN_TEST_PROJ_DIR = new File("test/test-projects/nested-inline-plugins/app")
    
    PluginBuildSettings createPluginBuildSettings(File projectDir = TEST_PROJ_DIR) {
        def settings = new BuildSettings(new File("."), projectDir)
        settings.loadConfig()
        return new PluginBuildSettings(settings)
    }

    void testGetPluginSourceFiles() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()
        def sourceFiles = pluginSettings.getPluginSourceFiles()
    }

    void testGetMetadataForPlugin() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        def xml = pluginSettings.getMetadataForPlugin("hibernate")

        assertNotNull "should have returned xml",xml

        assertEquals "hibernate", xml.@name.text()
        // exercise cache
        assertNotNull "cache xml was null, shoudln't have been", pluginSettings.getMetadataForPlugin("hibernate")
    }

    void testGetPluginDirForName() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        assertNotNull "should have found plugin dir",pluginSettings.getPluginDirForName("hibernate")
        assertNotNull "should have found plugin dir",pluginSettings.getPluginDirForName("hibernate")
    }

    void testGetPluginLibDirs() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        assertEquals 2, pluginSettings.getPluginLibDirectories().size()
        assertEquals 2, pluginSettings.getPluginLibDirectories().size()
    }

    void testGetPluginDescriptors() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()
        assertEquals 2, pluginSettings.getPluginDescriptors().size()
        assertEquals 2, pluginSettings.getPluginDescriptors().size()
    }

    void testGetAllArtefactResources() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        // called twice to exercise caching
        assertEquals 6, pluginSettings.getArtefactResources().size()
        assertEquals 6, pluginSettings.getArtefactResources().size()
    }

    void testGetAvailableScripts() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()
        def scripts = pluginSettings.getAvailableScripts()
        def nonUserScripts = []
        for (script in scripts) {
            if (!script.path.startsWith("${System.getProperty('user.home')}/.grails/scripts")) {
                nonUserScripts << script
            }
        }
        assertEquals 48, nonUserScripts.size()
    }

    void testGetPluginScripts() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()
        def scripts = pluginSettings.getPluginScripts()
        assertEquals 6, scripts.size()
    }

    void testGetPluginXmlMetadata() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        // called twice to exercise caching
        assertEquals 2, pluginSettings.getPluginXmlMetadata().size()
        assertEquals 2, pluginSettings.getPluginXmlMetadata().size()
    }

    void testGetPluginInfos() {
        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        def pluginInfos = pluginSettings.getPluginInfos()

        assertEquals 2, pluginInfos.size()

        assertNotNull "should contain hibernate", pluginInfos.find { it.name == 'hibernate' }
        assertNotNull "should contain webflow", pluginInfos.find { it.name == 'webflow' }
    }

    void testGetPluginResourceBundles() {
        def bundles = createPluginBuildSettings().pluginResourceBundles

        // Check the bundles that there are 3 bundles (2 from Hibernate, 1 from WebFlow).
        assertEquals 4, bundles.size()

        // Check that the caching doesn't break anything.
        assertEquals 4, bundles.size()
    }

    void testGetImplicitPluginDirectories() {
        def pluginDirs = createPluginBuildSettings().getImplicitPluginDirectories()

        assertEquals 2, pluginDirs.size()
        assertNotNull "hibernate plugin should be there",pluginDirs.find { it.filename.contains('hibernate') }
        assertNotNull "webflow plugin should be there",pluginDirs.find { it.filename.contains('webflow') }
    }

    void testGetPluginBaseDirectories() {
        def pluginSettings = createPluginBuildSettings()
        def baseDirs = pluginSettings.pluginBaseDirectories
        def pluginDir = new File(TEST_PROJ_DIR, "plugins").canonicalFile
        def globalPluginDir = pluginSettings.buildSettings.globalPluginsDir.canonicalFile

        assertEquals 2, baseDirs.size()
        assertTrue baseDirs.any { path -> new File(path).canonicalFile == pluginDir }
        assertTrue baseDirs.any { path -> new File(path).canonicalFile == globalPluginDir }
    }

    void testGetPluginDirectories() {

        PluginBuildSettings pluginSettings = createPluginBuildSettings()

        def pluginDirs = pluginSettings.getPluginDirectories()

        assertEquals 2, pluginDirs.size()

        assertNotNull "hibernate plugin should be there",pluginDirs.find { it.filename.contains('hibernate') }
        assertNotNull "webflow plugin should be there",pluginDirs.find { it.filename.contains('webflow') }
    }

    void testGetPluginJarFiles() {
        def jars = createPluginBuildSettings().pluginJarFiles

        // Make sure that all the JARs provided by the plugins are included in the list.
        assertEquals 3, jars.size()

        // Check that the caching doesn't break anything.
        assertEquals 3, jars.size()
    }

    void testGetSupportPluginInfos() {
        def pluginInfos = createPluginBuildSettings().supportedPluginInfos

        assertEquals 2, pluginInfos.size()
    }

    void testGetSupportPluginInfosWithPluginManager() {
        def pluginSettings = createPluginBuildSettings()
        pluginSettings.pluginManager = [
                getGrailsPlugin: { String pluginName ->
                    [supportsCurrentScopeAndEnvironment: { -> pluginName == "hibernate" }] as GrailsPlugin
                }] as GrailsPluginManager

        def pluginInfos = pluginSettings.supportedPluginInfos

        assertEquals 1, pluginInfos.size()
    }

    void testIsGlobalPluginLocation() {
        def pluginSettings = createPluginBuildSettings()
        assertFalse pluginSettings.isGlobalPluginLocation([ getFile: { -> new File(".").absoluteFile} ] as Resource)
        assertFalse pluginSettings.isGlobalPluginLocation([ getFile: { -> new File(TEST_PROJ_DIR, "test")} ] as Resource)
        assertTrue pluginSettings.isGlobalPluginLocation([
                getFile: {
                    -> new File(pluginSettings.buildSettings.globalPluginsDir, "test")
                }] as Resource)
        assertTrue pluginSettings.isGlobalPluginLocation([
                getFile: {
                    -> new File(pluginSettings.buildSettings.globalPluginsDir, "test/../gwt")
                }] as Resource)
    }
    
    void testNestedInlinePlugins() {
        def pluginSettings = createPluginBuildSettings(NESTED_INLINE_PLUGIN_TEST_PROJ_DIR)
        def inlinePluginDirs = pluginSettings.inlinePluginDirectories*.file
        
        assertEquals("inline plugins found", 2, inlinePluginDirs.size())
        
        def pluginOneDir = inlinePluginDirs.find { it.name.endsWith("plugin-one") }
        assertNotNull("plugin one dir", pluginOneDir)
        assertTrue("plugin one dir exists", pluginOneDir.exists())
        
        def pluginTwoDir = inlinePluginDirs.find { it.name.endsWith("plugin-two") }
        assertNotNull("plugin two dir", pluginTwoDir)
        
        // This is the most important test.
        // 
        // plugin two is a dependency of plugin one, and is defined relative to it
        // which means the path it is defined with does not point to it if resolved
        // relative to the "root" app that included plugin one. This would produce 
        // a false positive if test/test-projects/nested-inline-plugins/plugin-two existed.
        assertTrue("plugin two dir exists", pluginTwoDir.exists())
        
        // Make sure no one has done the wrong thing and put a dir at test/test-projects/nested-inline-plugins/plugin-two
        def pluginTwoInSameDirAsRootApp = new File(NESTED_INLINE_PLUGIN_TEST_PROJ_DIR.parentFile, "plugin-two")
        assertTrue("should not be a plugin-two dir in same dir as root app", !pluginTwoInSameDirAsRootApp.exists())
    }
}
