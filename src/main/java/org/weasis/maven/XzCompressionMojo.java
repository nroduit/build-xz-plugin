/*******************************************************************************
 * Copyright (c) 2009-2023 Nicolas Roduit and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.maven;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

/**
 * Goal capable of compressing a list of jar files with the pack200 tool.
 */
@Mojo(name = "packFiles", defaultPhase = LifecyclePhase.PACKAGE)
public class XzCompressionMojo extends AbstractMojo {

    /**
     * The base directory to scan for JAR files using Ant-like inclusion/exclusion patterns.
     */
    @Parameter(property = "xz.archiveDirectory", required = true)
    private File archiveDirectory;
    /**
     * The directory where xz files are written.
     */
    @Parameter(property = "xz.outputDirectory")
    private File outputDirectory;
    /**
     * The Ant-like inclusion patterns used to select JAR files to process. The patterns must be relative to the
     * directory given by the parameter {@link #archiveDirectory}. By default, the pattern
     * <code>&#42;&#42;/&#42;.?ar</code> is used.
     *
     */
    @Parameter(defaultValue =  "**/*.?ar")
    private String[] includes;

    /**
     * The Ant-like exclusion patterns used to exclude JAR files from processing. The patterns must be relative to the
     * directory given by the parameter {@link #archiveDirectory}.
     *
     */
    @Parameter
    private String[] excludes;

    @Parameter(defaultValue = "9")
    private Integer xzCompressionLevel;
    


    @Override
    public void execute() throws MojoExecutionException {

        getLog().debug("starting xz compression");

        if (archiveDirectory != null) {
            if (outputDirectory == null) {
                outputDirectory = archiveDirectory;
            }
            String archivePath = archiveDirectory.getAbsolutePath();
            if (!archivePath.endsWith(File.separator)) {
                archivePath = archivePath + File.separator;
            }
            int inputPathLength = archivePath.length();
            String includeList = (includes != null) ? StringUtils.join(includes, ",") : null;
            String excludeList = (excludes != null) ? StringUtils.join(excludes, ",") : null;

            List<File> jarFiles;
            try {
                jarFiles = FileUtils.getFiles(archiveDirectory, includeList, excludeList);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to scan archive directory for JARs: " + e.getMessage(), e);
            }
            getLog().info("Compressing " + jarFiles.size() + " files");
            for (Iterator<File> it = jarFiles.iterator(); it.hasNext();) {
                File jarFile = it.next();
                String filename = jarFile.getAbsolutePath().substring(inputPathLength);
                processArchive(filename);
            }
        }

    }

    private void processArchive(String filename) throws MojoExecutionException {
        File jarFile = new File(archiveDirectory, filename); // original file
        File folder = new File(archiveDirectory,FileUtils.removeExtension(filename)); // folder with uncompressed files
        File zipFile = new File(archiveDirectory,filename + ".zip"); // uncompressed zip file
        File xzFile = new File(outputDirectory, filename + ".xz"); // xz compressed file

        try {
            getLog().debug("uncompressing " + jarFile + " to " + zipFile);
            FileUtil.unzip(jarFile, folder);
            FileUtil.zip(folder, zipFile);
            FileUtils.deleteDirectory(folder);
            
            getLog().debug("compressing " + zipFile + " to " + xzFile);
            LZMA2Options options = new LZMA2Options();
            options.setPreset(xzCompressionLevel);

            try (FileInputStream in = new FileInputStream(zipFile);
                            XZOutputStream xzOut =
                                new XZOutputStream(new BufferedOutputStream(new FileOutputStream(xzFile)), options)) {
                IOUtil.copy(in, xzOut);
            } finally {
                FileUtil.deleteFile(zipFile);
            }
            getLog().debug("finished compressing " + xzFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to pack jar.", e);
        }
    }
}
