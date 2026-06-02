package app.otter.data.inspector

import org.junit.Test
import java.io.File

/**
 * Test RpaInspector with real RPA archive (browsing scenario).
 */
class RpaInspectorTest {

    @Test
    fun `browse RPA archive entries`() {
        val archivesDir = System.getProperty("archives.dir", "../archives")
        val rpaFile = File("$archivesDir/test_archive.rpa")
        require(rpaFile.exists()) { "Test archive not found: ${rpaFile.absolutePath}" }

        println("=== Testing RPA browsing with RpaInspector ===")

        val inspector = RpaInspector(rpaFile)
        
        // Count entries
        val count = kotlinx.coroutines.runBlocking { 
            inspector.countEntries() 
        }
        println("Total entries: $count")
        
        // Browse first 10 entries
        val entries = inspector.entries().take(10).toList()
        println("\nFirst 10 entries:")
        entries.forEach { entry ->
            println("  ${entry.path}: size=${entry.sizeBytes} bytes")
        }
        
        inspector.close()
        
        // Verify
        require(count > 0) { "Expected entries, got 0" }
        require(entries.size == 10) { "Expected 10 entries, got ${entries.size}" }
        require(entries[0].path.isNotEmpty()) { "First entry should have a path" }
        require(entries[0].sizeBytes > 0) { "First entry should have positive size" }
        
        println("\n✅ RPA browsing successful!")
    }
}
