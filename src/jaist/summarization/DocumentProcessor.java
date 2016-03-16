package jaist.summarization;

import edu.stanford.nlp.hcoref.data.InputDoc;
import edu.stanford.nlp.io.IOUtils;
import jaist.summarization.phrase.PhraseExtractor;
import jaist.summarization.unit.Phrase;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by chientran on 3/15/16.
 */
public class DocumentProcessor {
    private boolean isDucData;
    private HashMap<String, HashSet<String>> corefs = null;

    private List<InputDocument> docs;
    private PhraseMatrix indicatorMatrix;

    List<Phrase> nounPhrases;
    List<Phrase> verbPhrases;
    List<Phrase> allPhrases;

    HashSet<String> nouns;
    HashSet<String> verbs;

    public DocumentProcessor(boolean isDucData, PhraseMatrix indicatorMatrix){
        this.isDucData = isDucData;
        this.docs = new ArrayList<>();
        this.indicatorMatrix = indicatorMatrix;
        this.nounPhrases = new ArrayList<>();
        this.verbPhrases = new ArrayList<>();
        this.allPhrases = new ArrayList<>();

        this.nouns = new HashSet<>();
        this.verbs = new HashSet<>();
        this.corefs = new HashMap<>();
    }

    public void processDocuments(File[] fileNames) throws IOException{
        for (File filepath: fileNames){
            if (filepath.getName().startsWith(".")) continue;
            System.out.println(filepath.getAbsolutePath());

            String text = IOUtils.slurpFile(filepath);
            processDocument(text);
        }

        removeRedundantCorefs();
    }

    public void processDocument(String text){
        InputDocument inputDocument = new InputDocument(text, isDucData);
        this.docs.add(inputDocument);
        extractPhrases(inputDocument);
        this.corefs.putAll(inputDocument.getCoreferences());
    }

    private void extractPhrases(InputDocument inputDocument){
        PhraseExtractor extractor = new PhraseExtractor(inputDocument, indicatorMatrix);
        List<Phrase> phrases = extractor.extractAllPhrases();

        for (Phrase phrase : phrases) {
            if (phrase.isNP()) {
                nounPhrases.add(phrase);
                nouns.add(phrase.getContent());
            } else {
                verbPhrases.add(phrase);
                verbs.add(phrase.getContent());
            }
            allPhrases.add(phrase);
        }
    }

    private void removeRedundantCorefs(){
        Iterator<Map.Entry<String, HashSet<String>>> iter = corefs.entrySet().iterator();

        while(iter.hasNext()){
            Map.Entry<String, HashSet<String>> entry = iter.next();
            Set<String> mentions = entry.getValue();
            HashSet<String> newMentions = new HashSet<>(mentions);
            for(String mention: mentions){
                if (!nouns.contains(mention)){
                    newMentions.remove(mention);
                }
            }

            if (newMentions.size() < 2){
                iter.remove();
            }else{
                entry.setValue(newMentions);
            }
        }
    }

    public HashMap<String, HashSet<String>> getCorefs(){
        return this.corefs;
    }

    public List<Phrase> getNounPhrases(){
        return this.nounPhrases;
    }

    public List<Phrase> getVerbPhrases(){
        return this.verbPhrases;
    }

    public List<Phrase> getAllPhrases(){
        return this.allPhrases;
    }

    public HashSet<String> getNouns(){
        return this.nouns;
    }

    public HashSet<String> getVerbs(){
        return this.verbs;
    }

    public List<InputDocument> getDocs(){
        return this.docs;
    }
}
