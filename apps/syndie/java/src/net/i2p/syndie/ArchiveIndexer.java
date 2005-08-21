package net.i2p.syndie;

import java.io.*;
import java.text.*;
import java.util.*;
import net.i2p.I2PAppContext;
import net.i2p.data.*;
import net.i2p.syndie.data.*;

/**
 * Dig through the archive to build an index
 */
class ArchiveIndexer {
    private static final int RECENT_BLOG_COUNT = 10;
    private static final int RECENT_ENTRY_COUNT = 10;
    
    public static ArchiveIndex index(I2PAppContext ctx, Archive source) {
        LocalArchiveIndex rv = new LocalArchiveIndex();
        rv.setGeneratedOn(ctx.clock().now());
        
        File rootDir = source.getArchiveDir();
      
        File headerFile = new File(rootDir, Archive.HEADER_FILE);
        if (headerFile.exists()) {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(headerFile)));
                String line = null;
                while ( (line = in.readLine()) != null) {
                    StringTokenizer tok = new StringTokenizer(line, ":");
                    if (tok.countTokens() == 2)
                        rv.setHeader(tok.nextToken(), tok.nextToken());
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        
        // things are new if we just received them in the last day
        long newSince = ctx.clock().now() - 24*60*60*1000;
        
        rv.setVersion(Version.INDEX_VERSION);
        
        /** 0-lowestEntryId --> blog Hash */
        Map blogsByAge = new TreeMap();
        /** 0-entryId --> BlogURI */
        Map entriesByAge = new TreeMap();
        List blogs = source.listBlogs();
        rv.setAllBlogs(blogs.size());
        
        int newEntries = 0;
        int allEntries = 0;
        long newSize = 0;
        long totalSize = 0;
        int newBlogs = 0;
        
        for (int i = 0; i < blogs.size(); i++) {
            BlogInfo cur = (BlogInfo)blogs.get(i);
            Hash key = cur.getKey().calculateHash();
            String keyStr = Base64.encode(key.getData());
            File blogDir = new File(rootDir, Base64.encode(key.getData()));

            File metaFile = new File(blogDir, Archive.METADATA_FILE);
            long metadate = metaFile.lastModified();

            List entries = source.listEntries(key, -1, null, null);
            System.out.println("Entries under " + key + ": " + entries);
            /** tag name --> ordered map of entryId to EntryContainer */
            Map tags = new TreeMap();
            
            for (int j = 0; j < entries.size(); j++) {
                EntryContainer entry = (EntryContainer)entries.get(j);
                entriesByAge.put(new Long(0-entry.getURI().getEntryId()), entry.getURI());
                allEntries++;
                totalSize += entry.getCompleteSize();
                String entryTags[] = entry.getTags();
                for (int t = 0; t < entryTags.length; t++) {
                    if (!tags.containsKey(entryTags[t])) {
                        tags.put(entryTags[t], new TreeMap());
                    }
                    Map entriesByTag = (Map)tags.get(entryTags[t]);
                    entriesByTag.put(new Long(0-entry.getURI().getEntryId()), entry);
                    System.out.println("Entries under tag " + entryTags[t] + ":" + entriesByTag.values());
                }
                    
                if (entry.getURI().getEntryId() >= newSince) {
                    newEntries++;
                    newSize += entry.getCompleteSize();
                }
            }
            
            long lowestEntryId = -1;
            for (Iterator iter = tags.keySet().iterator(); iter.hasNext(); ) {
                String tagName = (String)iter.next();
                Map tagEntries = (Map)tags.get(tagName);
                long highestId = -1;
                if (tagEntries.size() <= 0) break;
                Long id = (Long)tagEntries.keySet().iterator().next();
                highestId = 0 - id.longValue();
                
                rv.addBlog(key, tagName, highestId);
                for (Iterator entryIter = tagEntries.values().iterator(); entryIter.hasNext(); ) {
                    EntryContainer entry = (EntryContainer)entryIter.next();
                    String indexName = Archive.getIndexName(entry.getURI().getEntryId(), entry.getCompleteSize());
                    rv.addBlogEntry(key, tagName, indexName);
                    if (!entryIter.hasNext())
                        lowestEntryId = entry.getURI().getEntryId();
                }
            }
            
            if (lowestEntryId > newSince)
                newBlogs++;
            
            blogsByAge.put(new Long(0-lowestEntryId), key);
        }
        
        rv.setAllEntries(allEntries);
        rv.setNewBlogs(newBlogs);
        rv.setNewEntries(newEntries);
        rv.setTotalSize(totalSize);
        rv.setNewSize(newSize);

        int i = 0;
        for (Iterator iter = blogsByAge.keySet().iterator(); iter.hasNext() && i < RECENT_BLOG_COUNT; i++) {
            Long when = (Long)iter.next();
            Hash key = (Hash)blogsByAge.get(when);
            rv.addNewestBlog(key);
        }
        i = 0;
        for (Iterator iter = entriesByAge.keySet().iterator(); iter.hasNext() && i < RECENT_ENTRY_COUNT; i++) {
            Long when = (Long)iter.next();
            BlogURI uri = (BlogURI)entriesByAge.get(when);
            rv.addNewestEntry(uri);
        }
        
        return rv;
    }
}