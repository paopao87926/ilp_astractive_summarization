package jaist.summarization;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import jaist.summarization.unit.Paragraph;
import jaist.summarization.utils.StringUtils;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

/**
 * Created by chientran on 3/3/16.
 */
public class InputDocument {
    private Annotation annotation;
    private HashSet<String> namedEntities;
    private Map<String, String> wordToLemmaMap;
    private HashMap<String, HashSet<String>> corefs;
    private static final String PARAGRAPH_SPLIT_REGEX = "(?m)(?=^\\s{4})";
    private ArrayList<Paragraph> paragraphs;
    private String headline;

    public InputDocument(String text){
        this(text, false);
    }
    public InputDocument(String text, boolean isDucData){
        if (isDucData){
            text = processDucDocument(text);
        }

        this.annotation = new Annotation(text);
        AnnotatorHub.getInstance().getPipeline().annotate(annotation);

        extractNamedEntities();
        extractCoreferences();
        buildWordToLemmaMap();
        prepareParagraphs();
    }

    private String processDucDocument(String text){
        try{
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(text)));

            doc.getDocumentElement().normalize();
            Node rootNode = doc.getDocumentElement();
            Node headlineNode = doc.getDocumentElement().getElementsByTagName("HEADLINE").item(0);
            headline = headlineNode.getTextContent();
            Node textNode = doc.getElementsByTagName("TEXT").item(0);
            String documentText = textNode.getTextContent();
            return documentText;
        }catch(Exception e){
            e.printStackTrace();
        }
        return "";
    }

    private void extractNamedEntities(){
        this.namedEntities = new HashSet<>();

        for (CoreMap mention : annotation.get(CoreAnnotations.MentionsAnnotation.class)) {
            String ner = mention.get(CoreAnnotations.TextAnnotation.class);
            namedEntities.add(ner);
        }
    }

    private void prepareParagraphs(){
        paragraphs = new ArrayList<>();
        String[] paragraphTexts = annotation.toString().split(PARAGRAPH_SPLIT_REGEX);

        for (String paragraphText: paragraphTexts){
            HashMap<String, Integer> paragraphConceptsWithFrequency = extractConceptsFromString(paragraphText);
            Paragraph paragraph = new Paragraph(paragraphConceptsWithFrequency);

            paragraphs.add(paragraph);
        }
    }

    private void buildWordToLemmaMap(){
        wordToLemmaMap = new HashMap<>();

        List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
        tokens = StopwordRemover.removeStopwords(tokens);

        for (CoreLabel token: tokens){
            wordToLemmaMap.put(token.originalText(), token.lemma());
        }
    }

    private void extractCoreferences() {
        corefs = new HashMap<>();

        Map<Integer, edu.stanford.nlp.hcoref.data.CorefChain> corefChains = annotation.get(edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation.class);

        for (edu.stanford.nlp.hcoref.data.CorefChain c : corefChains.values()) {

            edu.stanford.nlp.hcoref.data.CorefChain.CorefMention representative = c.getRepresentativeMention();
            String key = representative.mentionSpan;

            if (c.getMentionsInTextualOrder().size() == 1) {
                continue;
            }

            if (!corefs.containsKey(key)){
                corefs.put(key, new HashSet<String>());
                corefs.get(key).add(key);
            }

            for (edu.stanford.nlp.hcoref.data.CorefChain.CorefMention m : c.getMentionsInTextualOrder()) {
                if (m == representative) {
                    continue;
                }

                corefs.get(key).add(m.mentionSpan);
            }
        }
    }

    public HashMap<String, Integer> extractConceptsFromString(String content){
        HashMap<String, Integer> concepts = new HashMap<>();

        List<String> originalUnigrams = StringUtils.generateUnigrams(content);
        LinkedHashSet<String> unigrams = new LinkedHashSet<>();
        for (String unigram: originalUnigrams){
            if (wordToLemmaMap.containsKey(unigram)){
                String lemma = wordToLemmaMap.get(unigram);
                unigrams.add(lemma);

                increaseFrequency(concepts, lemma);

            }
        }

        List<String> bigrams = StringUtils.generateBigrams(new ArrayList<>(unigrams));

        for(String gram: bigrams){
            increaseFrequency(concepts, gram);
        }

        for (String ner: namedEntities){
            if (content.contains(ner)){

                increaseFrequency(concepts, ner);
            }
        }

        return concepts;
    }

    private void increaseFrequency(HashMap<String, Integer> conceptsToFrequency, String key){
        Integer count = 0;

        if (conceptsToFrequency.containsKey(key)){
            count = conceptsToFrequency.get(key);
        }

        count += 1;

        conceptsToFrequency.put(key, count);
    }

    public List<CoreMap> getSentences(){
        return annotation.get(CoreAnnotations.SentencesAnnotation.class);
    }

    public List<Paragraph> getParagraphs(){
        return this.paragraphs;
    }

    public HashMap<String, HashSet<String>> getCoreferences(){
        return this.corefs;
    }

}

