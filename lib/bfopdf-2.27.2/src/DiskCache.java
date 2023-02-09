// $Id: DiskCache.java 29735 2018-10-02 23:05:49Z mike $

package org.faceless.pdf2;

import java.io.*;
import org.faceless.util.BackedInputStream;
import org.faceless.util.Langolier;
import org.faceless.util.FastByteArrayOutputStream;

/**
 * A general purpose {@link Cache} which optionally writes closed streams
 * to files on disk. This class has been tested on UNIX and Windows under
 * multi-threaded environments, and we see no reason why it shouldn't work
 * under other platforms as well. Here's a simple example:
 * <pre class="brush:java">
 *   Cache cache = new DiskCache(System.getProperty("java.io.tmpdir")+"/pdftemp.", 8192);
 *   PDF.setCache(cache);
 * </pre>
 * @see PDF#setCache
 * @since 2.2.2
 */
public class DiskCache implements Cache {

    private final File directory;             // The directory to create the temp files in
    private final String prefix;              // The prefix of the filename
    private final int threshold;              // The smallest stream to write to disk
    boolean debug;

    /**
     * Create a new DiskCache
     *
     * @param prefix The prefix to begin the cached filenames with. For instance, if
     * prefix was "/tmp/cache/mycache.", the cache files would be "/tmp/cache/mycache.NNN",
     * "/tmp/cache/mycache.NNN" etc. The fixed prefix of the filename will be lengthened to
     * at least 3 characters long if required.
     *
     * @param threshold the minimum number of bytes that are considered worth caching to
     * disk, or 0 to prevent caching altogether
     */
    public DiskCache(String prefix, int threshold) {
        if (prefix == null || threshold == 0) {
            this.threshold = Integer.MAX_VALUE;
            this.directory = null;
            this.prefix = null;
        } else {
            File file = new File(prefix);
            if (file.isDirectory()) {
                this.directory = file;
                this.prefix = "pdfcache";
            } else {
                this.directory = file.getParentFile();
                // Minimum prefix length for File.createTempFile is three characters
                prefix = file.getName();
                if (prefix.length() < 3) {
                    prefix += "pdf".substring(prefix.length()); // Arbitrary but why not?
                }
                this.prefix = prefix;
            }
            this.threshold = threshold;
        }
    }


    public Cache.Entry newEntry(int size) {
        return new DiskCacheEntry(size);
    }

    /**
     * Return the next filename to be used in the cache. Use File.createTempFile
     * in case we have two JVMs running a cache on the same folder.
     */
    private File nextFileName() throws IOException {
        return File.createTempFile(prefix, "", directory);
    }


    private class DiskCacheEntry extends OutputStream implements Cache.Entry {
        private OutputStream out;
        private File file;                      // null if data held in memory
        private int count;
        private boolean closed;
        private BackedInputStream stream;

        public DiskCacheEntry(int size) {
            if (prefix != null && size >= threshold) {
                try {
                    toDisk();
                } catch (IOException e) {
                    throw toRuntimeException(e);
                }
            } else {
                out = new MyByteArrayOutputStream(size);
            }
        }

        public OutputStream getOutputStream() {
            if (closed) {
                throw new IllegalStateException("Can't write to closed stream \""+file+"\"");
            }
            return this;
        }

        public BackedInputStream getInputStream() {
            if (stream == null) {
                if (file != null) {
                    try {
                        if (!closed) {
                            out.flush();
                        }
                        stream = BackedInputStream.getInstance(file);
                    } catch (IOException e) {
                        throw toRuntimeException(e);
                    }
                } else {
                    stream = BackedInputStream.getInstance(((MyByteArrayOutputStream)out).buf, 0, count);
                }
            }
            return stream;
        }

        public int size() {
            return count;
        }

        public void writeTo(OutputStream stream) throws IOException {
            if (out instanceof MyByteArrayOutputStream) {
                stream.write(((MyByteArrayOutputStream)out).buf, 0, count);
            } else {
                BackedInputStream in = getInputStream();
                in.writeTo(stream);
                in.close();
            }
        }

        public void close() {
            if (!closed) {
                try {
                    out.close();
                    if (file != null) {
                        out = null;
                    }
                } catch (IOException e) {
                    throw toRuntimeException(e);
                }
                closed = true;
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw toRuntimeException(e);
                } finally {
                    stream = null;
                }
            }
        }

        public String toString() {
            return "<filecache size="+size()+" file="+file+">";
        }


        //-----  OutputStream methods from here on ----

        public void write(int b) throws IOException {
            out.write(b);
            count++;
            if (prefix != null && file == null && count > threshold) {
                toDisk();
            }
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (prefix != null && file == null && count + len >= threshold) {
                toDisk();
            }
            out.write(b, off,len);
            count += len;
        }

        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        private void toDisk() throws IOException {
            file = nextFileName();
            if (!debug) {
                file.deleteOnExit();
                Langolier.deleteFile(this, file);
            }
            byte[] buf = out == null ? null : ((MyByteArrayOutputStream)out).buf;
            out = new BufferedOutputStream(new FileOutputStream(file), 4096);
            if (buf != null) {
                out.write(buf, 0, count);
            }
        }
    }

    private static final class MyByteArrayOutputStream extends OutputStream {
        private int count;
        byte[] buf;

        MyByteArrayOutputStream(int size) {
            buf = new byte[size == 0 ? 16 : size];
        }

        public void write(int b) {
            if (count == buf.length) {
                extend(count + 8);
            }
            buf[count++] = (byte)b;
        }

        public void write(byte[] b, int off, int len) {
            if (len == 0) {
                return;
            } else if (off < 0 || len < 0 || off + len > b.length) {
                throw new IndexOutOfBoundsException(off+"+"+len+" outside "+b.length);
            }
            if (count + len > buf.length) {
                extend(count + len);
            }
            System.arraycopy(b, off, buf, count, len);
            count += len;
        }

        public void write(byte[] b) {
            write(b, 0, b.length);
        }

        /**
         * Increase the capacity of the internal buffer
         */
        private void extend(int newlen) {
            if (newlen > buf.length) {
                if (newlen < buf.length + (buf.length>>1)) {
                    newlen = buf.length + (buf.length>>1);
                }
                byte[] newbuf = new byte[newlen];
                System.arraycopy(buf, 0, newbuf, 0, count);
                buf = newbuf;
            }
        }
    }

    private static final RuntimeException toRuntimeException(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException)e;
        } else {
            RuntimeException e2 = new RuntimeException(e.toString());
            e2.initCause(e);
            return e2;
        }
    }

}
