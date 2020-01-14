package com.testfairy.app;

import java.io.File;

class FileUtils {
    static public void deleteEntirely(File fileOrFolder) {
        if (!fileOrFolder.exists()) {
            return;
        }

        if (fileOrFolder.isDirectory()) {
            deleteFolderRecursively(fileOrFolder);
        } else {
            fileOrFolder.delete();
        }
    }

    static private void deleteFolderRecursively(File folder) {
        if (folder.isDirectory()) {
            for (File f : folder.listFiles()) {
                deleteEntirely(f);
            }

            folder.delete();
        }
    }
}
