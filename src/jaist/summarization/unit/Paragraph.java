package jaist.summarization.unit;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.EntityMentionsAnnotator;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;

import jaist.summarization.AnnotatorHub;
import jaist.summarization.StopwordRemover;

import java.util.*;

/**
 * Created by chientran on 9/29/15.
 */
public class Paragraph {
    private Annotation doc = null;
    private HashMap<String, Integer> unigramFrequency = null;
    private HashMap<String, Integer> bigramFrequency = null;
    private HashMap<String, Integer> nerFrequency = null;
    private List<CoreLabel> tokens = null;

    public Paragraph(String paragraphText){
        unigramFrequency = new HashMap<>();
        bigramFrequency = new HashMap<>();
        nerFrequency = new HashMap<>();

        doc = new Annotation(paragraphText);
        AnnotatorHub.getInstance().getPipeline().annotate(doc);
        AnnotatorHub.getInstance().getEntityMentionsAnnotator().annotate(doc);

        tokens = StopwordRemover.removeStopwords(doc.get(CoreAnnotations.TokensAnnotation.class));

        prepareUnigrams();
        prepareBigrams();
        prepareNER();
    }

    private void prepareUnigrams(){
        for(CoreLabel token: tokens){
            String textLemma = token.get(CoreAnnotations.LemmaAnnotation.class);
            increaseFrequency(unigramFrequency, textLemma);
        }
    }

    private void prepareBigrams(){
        List<List<CoreLabel>> bigramsTokens = CollectionUtils.getNGrams(tokens, 2, 2);

        for(List<CoreLabel> token: bigramsTokens){
            String first = token.get(0).get(CoreAnnotations.LemmaAnnotation.class);
            String second = token.get(1).get(CoreAnnotations.LemmaAnnotation.class);

            String bigram = String.join(" ", new String[]{first, second});

            increaseFrequency(bigramFrequency, bigram);
        }
    }

    private void prepareNER(){
        EntityMentionsAnnotator annotator = AnnotatorHub.getInstance().getEntityMentionsAnnotator();

        annotator.annotate(this.doc);

        List<CoreMap> mentions = this.doc.get(CoreAnnotations.MentionsAnnotation.class);

        for(CoreMap mention: mentions){
            String name = mention.get(CoreAnnotations.TextAnnotation.class);

            increaseFrequency(nerFrequency, name);
        }
    }

    private void increaseFrequency(HashMap<String, Integer> hash, String key){
        Integer count = 0;

        if (hash.containsKey(key)){
            count = hash.get(key);
        }

        count += 1;

        hash.put(key, count);
    }

    private Integer getFrequency(HashMap<String, Integer> hash, String key){
        if (hash.containsKey(key)) {
            return hash.get(key);
        }else{
            return 0;
        }
    }

    public Integer countFrequency(String concept){
        return getFrequency(unigramFrequency, concept) + getFrequency(bigramFrequency, concept)
                + getFrequency(nerFrequency, concept);
    }
}
