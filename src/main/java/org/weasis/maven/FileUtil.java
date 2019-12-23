/*******************************************************************************
 * Copyright (c) 2009-2019 Nicolas Roduit and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.maven;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    public static final int FILE_BUFFER = 4096;

    private FileUtil() {
    }
    
    public static boolean deleteFile(File file) {
        try {
            Files.delete(file.toPath());
        } catch (Exception e) {
            System.err.println("Cannot delete: " + e.getMessage()); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public static void safeClose(final AutoCloseable object) {
        if (object != null) {
            try {
                object.close();
            } catch (Exception e) {
                System.err.println("Cannot close AutoCloseable: "  + e.getMessage()); //$NON-NLS-1$
            }
        }
    }

    public static void zip(File directory, File zipfile) throws IOException {
        if (zipfile == null || directory == null) {
            return;
        }
        URI base = directory.toURI();
        Deque<File> queue = new LinkedList<>();
        queue.push(directory);

        // The resources will be closed in reverse order of the order in which they are created in try().
        // Zip stream must be close before out stream.
        try (OutputStream out = new FileOutputStream(zipfile); ZipOutputStream zout = new ZipOutputStream(out)) {
            zout.setMethod(ZipOutputStream.STORED);
            zout.setLevel(Deflater.NO_COMPRESSION);
            while (!queue.isEmpty()) {
                File dir = queue.pop();
                for (File entry : dir.listFiles()) {
                    String name = base.relativize(entry.toURI()).getPath();
                    if (entry.isDirectory()) {
                        queue.push(entry);
                        if (entry.list().length == 0) {
                            name = name.endsWith("/") ? name : name + "/"; //$NON-NLS-1$ //$NON-NLS-2$
                            ZipEntry zipEntry = new ZipEntry(name);
                            zipEntry.setSize(0);
                            zipEntry.setCompressedSize(0);
                            zipEntry.setCrc(0);
                            zout.putNextEntry(zipEntry);
                        }
                    } else {
                        ZipEntry zipEntry = new ZipEntry(name);
                        zipEntry.setSize(entry.length());
                        zipEntry.setCompressedSize(entry.length());
                        zipEntry.setCrc(computeCrc(entry));
                        zout.putNextEntry(zipEntry);
                        copyZip(entry, zout);
                        zout.closeEntry();
                    }
                }
            }
        }
    }

    public static void unzip(InputStream inputStream, File directory) throws IOException {
        if (inputStream == null || directory == null) {
            return;
        }

        try (BufferedInputStream bufInStream = new BufferedInputStream(inputStream);
                        ZipInputStream zis = new ZipInputStream(bufInStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(directory, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    copyZip(zis, file);
                }
            }
        } finally {
            FileUtil.safeClose(inputStream);
        }
    }

    public static void unzip(File zipfile, File directory) throws IOException {
        if (zipfile == null || directory == null) {
            return;
        }
        try (ZipFile zfile = new ZipFile(zipfile)) {
            Enumeration<? extends ZipEntry> entries = zfile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File file = new File(directory, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (InputStream in = zfile.getInputStream(entry)) {
                        copyZip(in, file);
                    }
                }
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) {
            return;
        }
        byte[] buf = new byte[FILE_BUFFER];
        int offset;
        while ((offset = in.read(buf)) > 0) {
            out.write(buf, 0, offset);
        }
        out.flush();
    }

    private static void copyZip(File file, OutputStream out) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            copy(in, out);
        }
    }

    private static void copyZip(InputStream in, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            copy(in, out);
        }
    }

    

    /**
     * Computes the CRC checksum for the given file.
     *
     * @param file The file to compute checksum for.
     * @return A CRC32 checksum.
     * @throws IOException If an I/O error occurs.
     */
    private static long computeCrc(File file) throws IOException {
        CRC32 crc = new CRC32();
        InputStream in = new FileInputStream(file);

        try {

            byte[] buf = new byte[8192];
            int n = in.read(buf);
            while (n != -1) {
                crc.update(buf, 0, n);
                n = in.read(buf);
            }

        } finally {
            in.close();
        }

        return crc.getValue();
    }
}
