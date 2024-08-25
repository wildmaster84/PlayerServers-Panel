package me.wild.utils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import net.cakemine.playerservers.bungee.PlayerServers;

public class FileUtils {

    /**
     * Copies a file or directory from the source to the destination. 
     * If the source is a directory, it will recursively copy all its contents.
     * 
     * @param source The source file or directory.
     * @param destination The destination file or directory.
     * @throws IOException if an I/O error occurs.
     */
    @SuppressWarnings("resource")
	public static void copy(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            String[] children = source.list();
            if (children != null) {
                for (String fileName : children) {
                    File sourceChild = new File(source, fileName);
                    File destinationChild = new File(destination, fileName);
                    copy(sourceChild, destinationChild);
                }
            }
        } else {
            try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
                 FileChannel destChannel = new FileOutputStream(destination).getChannel()) {
                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            }
        }
    }
    
    /**
     * Recursively copies a directory from the source path to the target path.
     * 
     * @param source The source directory.
     * @param target The target directory.
     * @throws IOException if an I/O error occurs.
     */
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectory(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Deletes a directory and all its contents.
     * 
     * @param directory The directory to delete.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Deletes a file or directory. If the file is a directory, it deletes all its contents.
     * 
     * @param exchange The HTTP server exchange.
     * @param file The file or directory to delete.
     * @return true if the file was deleted, false otherwise.
     * @throws IOException if an I/O error occurs.
     */
    public static boolean deleteFile(HttpServerExchange exchange, File file) throws IOException {
        if (!file.exists()) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.getResponseSender().send("File not found.");
            return false;
        }

        if (file.isDirectory()) {
            FileUtils.deleteDirectory(file.toPath());
        } else {
            Files.delete(file.toPath());
        }

        return true;
    }
    
    /**
     * Checks if a file is within the server's base directory.
     * 
     * @param server The PlayerServer instance.
     * @param file The file to check.
     * @return true if the file is within the server directory, false otherwise.
     * @throws IOException if an I/O error occurs.
     */
    public static boolean isWithinServerDirectory(net.cakemine.playerservers.bungee.objects.PlayerServer server, File file) throws IOException {
    	File directory = new File("plugins/PlayerServers/servers", server.getUUID().toString());
        Path serverBasePath = directory.toPath().toRealPath();
        Path requestedFilePath = file.toPath().toRealPath();
        return requestedFilePath.startsWith(serverBasePath);
    }
    
    /**
     * Compresses a file or directory into a ZIP archive.
     * 
     * @param fileToZip The file or directory to compress.
     * @param outputZipFile The output ZIP file.
     * @return Boolean if job completed or not.
     */
    public static boolean compress(File fileToZip, File outputZipFile) {
    	if (fileToZip == null || outputZipFile == null) {
    		return false;
    	}
        try (FileOutputStream fos = new FileOutputStream(outputZipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            compressFileToZip(fileToZip, fileToZip.getName(), zipOut);
            return true;
        } catch (IOException e) {
        	return false;
        }
    }

    private static void compressFileToZip(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) return;
        if (fileToZip.isDirectory()) {
            if (!fileName.endsWith("/")) fileName += "/";
            zipOut.putNextEntry(new ZipEntry(fileName));
            zipOut.closeEntry();
            for (File childFile : fileToZip.listFiles()) {
                compressFileToZip(childFile, fileName + childFile.getName(), zipOut);
            }
            return;
        }
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            zipOut.putNextEntry(new ZipEntry(fileName));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zipOut.write(buffer, 0, length);
            }
        }
    }
    
    /**
     * Extracts a ZIP archive to the target directory.
     * 
     * @param zipFile The ZIP file to extract.
     * @param targetDirectory The target directory where the contents will be extracted.
     * @throws IOException if an I/O error occurs.
     */
    public static boolean decompress(File zipFile, File targetDirectory) {
    	if (zipFile == null || targetDirectory == null) {
    		return false;
    	}
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File newFile = newFile(targetDirectory, zipEntry);
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (IOException e) {
        	return false;
        }
    }

    private static File newFile(File targetDirectory, ZipEntry zipEntry) throws IOException {
        File destFile = new File(targetDirectory, zipEntry.getName());
        String destDirPath = targetDirectory.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target directory: " + zipEntry.getName());
        }
        return destFile;
    }
}
