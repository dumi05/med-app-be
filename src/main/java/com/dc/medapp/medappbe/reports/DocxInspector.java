package com.dc.medapp.medappbe.reports;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public final class DocxInspector {
    public static final int MAX_ENTRIES = 1000;
    public static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;

    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCUMENT = "word/document.xml";
    private static final String MACROS = "word/vbaProject.bin";

    private DocxInspector(){}

    public static void requireWordDocument(InputStream content){
        var names = new HashSet<String>();
        long total = 0;
        var buffer = new byte[8192];
        try(var zip = new ZipInputStream(content)){
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null){
                if(names.size() >= MAX_ENTRIES){
                    throw new IllegalArgumentException("Il documento Word non è valido (troppi elementi).");
                }
                var name = entry.getName();
                if(name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":")){
                    throw new IllegalArgumentException("Il documento Word non è valido (percorso non ammesso).");
                }
                names.add(name);
                int read;
                while ((read = zip.read(buffer)) != -1){
                    total += read;
                    if(total > MAX_UNCOMPRESSED_BYTES){
                        throw new IllegalArgumentException("Il documento Word non è valido (troppo grande una volta aperto).");
                    }
                }
            }
        } catch (ZipException e){
            throw new IllegalArgumentException("Il documento Word non è valido.",e);
        } catch (IOException e){
            throw new IllegalArgumentException("Il documento Word non è leggibile.",e);
        }
        requireEntries(names);
    }

    private static void requireEntries(Set<String> names){
        if(!names.contains(CONTENT_TYPES) || !names.contains(DOCUMENT)){
            throw new IllegalArgumentException("Il file non è un documento Word (.docx).");
        }
        if(names.contains(MACROS)){
            throw new IllegalArgumentException("I documenti Word con macro non sono ammessi.");
        }
    }
}
