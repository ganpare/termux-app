package com.termux.app.claude;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ClaudeChatParserTest {

        @Test
        public void testGetAgentComments() throws IOException {
                // Create a temp file with sample JSONL content
                File tempFile = File.createTempFile("test_chat", ".jsonl");
                tempFile.deleteOnExit();

                try (FileWriter writer = new FileWriter(tempFile)) {
                        // 1. User message (should be ignored)
                        writer.write("{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"Hello\"}}\n");

                        // 2. Assistant message with text (should be captured)
                        writer.write(
                                        "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"First comment\"}]}}\n");

                        // 3. Assistant message with tool use (tool should be ignored, text kept if any)
                        writer.write(
                                        "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Thinking about tool\"},{\"type\":\"tool_use\",\"name\":\"ls\"}]}}\n");

                        // 4. Tool result (should be ignored)
                        writer.write(
                                        "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"content\":\"file list...\"}]}}\n");

                        // 5. Assistant message with text mixed
                        writer.write(
                                        "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Final answer\"}]}}\n");
                }

                // Run parser
                List<String> comments = ClaudeChatParser.getAgentComments(tempFile.getAbsolutePath());

                // Verify results
                assertEquals(3, comments.size());
                assertEquals("First comment", comments.get(0));
                assertEquals("Thinking about tool", comments.get(1));
                assertEquals("Final answer", comments.get(2));
        }
}
