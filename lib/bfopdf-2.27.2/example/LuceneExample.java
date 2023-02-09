// $Id: LuceneExample.java 18793 2013-12-18 13:39:35Z mike $

import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.*;
import org.apache.lucene.store.*;
import org.faceless.pdf2.*;
import java.io.*;
import java.util.*;

/**
 * A Simple example demonstrating how to index then search a PDF using the
 * Apache Lucene library. The only PDF-specific bit is the "createIndex" method.
 */
public class LuceneExample {

    static final Version version = Version.LUCENE_46;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java LuceneExample [<file.pdf> ...]\n\n");
            System.exit(1);
        }

        Directory dir = new RAMDirectory();
        Analyzer analyzer = new StandardAnalyzer(version);
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(version, analyzer));
        for (int i=0;i<args.length;i++) {
            indexPDF(writer, args[i]);
        }
        writer.close();

        searchIndex("World", dir, analyzer);
    }

    /**
     * Create the index. This has the only PDF library specific
     * code in this example - it calls the PDFParser.getLuceneDocument
     * method.
     */
    static void indexPDF(IndexWriter writer, String file) throws IOException {
        PDF pdf = new PDF(new PDFReader(new File(file)));
        PDFParser parser = new PDFParser(pdf);

        Document doc = parser.getLuceneDocument(true, true, true);
        doc.add(new TextField("filename", file, Field.Store.YES));
        writer.addDocument(doc);
    }

    /**
     * Search the index for the specified text
     */
    static void searchIndex(String querystring, Directory index, Analyzer analyzer) throws IOException, ParseException {
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        QueryParser parser = new QueryParser(version, "all", analyzer);
        Query query = parser.parse(querystring);
        TopDocs docs = searcher.search(query, 10);
        ScoreDoc[] hits = docs.scoreDocs;

        System.out.println("Searching for \""+querystring+"\" in "+reader.numDocs()+" document(s): "+hits.length+" hit(s)");
        for (int i=0;i<hits.length;i++){
            Document doc = searcher.doc(hits[i].doc);
            System.out.println("* "+doc.get("filename"));
//            System.out.println(doc.get("body"));
        }
    }

}
