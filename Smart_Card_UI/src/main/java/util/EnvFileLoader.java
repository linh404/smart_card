package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * EnvFileLoader - Load environment variables from .env file
 * 
 * Java does not automatically read .env files, so this utility class
 * reads the .env file and sets environment variables in the current JVM.
 */
public class EnvFileLoader {
    
    private static boolean loaded = false;
    
    /**
     * Load .env file from current directory or project root
     */
    public static void load() {
        if (loaded) {
            return; // Already loaded
        }
        
        String userDir = System.getProperty("user.dir");
        System.out.println("[EnvFileLoader] Current working directory: " + userDir);
        
        // Try multiple locations
        String[] possiblePaths = {
            userDir + File.separator + ".env",                              // Current directory
            userDir + File.separator + "Smart_Card_UI" + File.separator + ".env",  // Smart_Card_UI subdirectory
            userDir + File.separator + ".." + File.separator + ".env",     // Parent directory
            ".env",                                                         // Relative current
            "Smart_Card_UI" + File.separator + ".env"                      // Relative Smart_Card_UI
        };
        
        File envFile = null;
        for (String path : possiblePaths) {
            File testFile = new File(path);
            String absPath = testFile.getAbsolutePath();
            System.out.println("[EnvFileLoader] Trying: " + absPath + " (exists: " + testFile.exists() + ")");
            if (testFile.exists() && testFile.isFile()) {
                envFile = testFile;
                System.out.println("[EnvFileLoader] Found .env file at: " + absPath);
                break;
            }
        }
        
        // If still not found, try to find it relative to the class location
        if (envFile == null) {
            try {
                // Get the directory where this class is located
                String classPath = EnvFileLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
                
                // Try to find .env relative to classpath
                Path classPathDir = Paths.get(classPath).getParent();
                if (classPathDir != null) {
                    // Try going up to find Smart_Card_UI
                    Path current = classPathDir;
                    for (int i = 0; i < 5; i++) {
                        File testFile = current.resolve(".env").toFile();
                        if (testFile.exists()) {
                            envFile = testFile;
                            break;
                        }
                        File smartCardUI = current.resolve("Smart_Card_UI").resolve(".env").toFile();
                        if (smartCardUI.exists()) {
                            envFile = smartCardUI;
                            break;
                        }
                        current = current.getParent();
                        if (current == null) break;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (envFile == null || !envFile.exists()) {
            System.out.println("[EnvFileLoader] .env file not found. Tried locations:");
            for (String path : possiblePaths) {
                System.out.println("  - " + new File(path).getAbsolutePath());
            }
            System.out.println("[EnvFileLoader] Please create .env file in Smart_Card_UI directory with: K_MASTER=your_key");
            return;
        }
        
        System.out.println("[EnvFileLoader] Loading .env file from: " + envFile.getAbsolutePath());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            int lineNumber = 0;
            int loadedCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=VALUE format
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    // Set system property
                    System.setProperty(key, value);
                    loadedCount++;
                    
                    // Mask sensitive values in log
                    String displayValue = value;
                    if (key.contains("PASSWORD") || key.contains("SECRET") || key.contains("KEY") || key.contains("MASTER")) {
                        if (value.length() > 8) {
                            displayValue = value.substring(0, 4) + "..." + value.substring(value.length() - 4);
                        } else {
                            displayValue = "***";
                        }
                    }
                    
                    System.out.println("[EnvFileLoader] Loaded: " + key + " = " + displayValue);
                } else {
                    System.out.println("[EnvFileLoader] Warning: Invalid format at line " + lineNumber + ": " + line);
                }
            }
            
            loaded = true;
            System.out.println("[EnvFileLoader] .env file loaded successfully! (" + loadedCount + " variables)");
        } catch (IOException e) {
            System.err.println("[EnvFileLoader] Error loading .env file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get environment variable value, checking both:
     * 1. System environment variables (System.getenv)
     * 2. System properties (System.getProperty) - loaded from .env file
     * 
     * @param key Environment variable name
     * @return Value or null if not found
     */
    public static String getEnv(String key) {
        // First check system environment variables
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        // Then check system properties (loaded from .env)
        value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        return null;
    }
    
    /**
     * Check if .env file has been loaded
     */
    public static boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Reset loaded flag (for testing)
     */
    public static void reset() {
        loaded = false;
    }
}

