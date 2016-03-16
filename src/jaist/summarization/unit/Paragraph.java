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
    private List<CoreLabel> tokens = null;
    private HashMap<String, Integer> conceptsToFrequency;

    public Paragraph(HashMap<String, Integer> conceptsToFrequency){
        this.conceptsToFrequency = conceptsToFrequency;
    }

    public Set<String> getConcepts(){
        return conceptsToFrequency.keySet();
    }
    public Integer countFrequency(String concept){
        if (conceptsToFrequency.containsKey(concept)){
            return conceptsToFrequency.get(concept);
        }else{
            return 0;
        }
    }
}
