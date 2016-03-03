package jaist.summarization;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import intoxicant.analytics.coreNlp.StopwordAnnotator;
import jaist.summarization.unit.Paragraph;
import jaist.summarization.unit.Phrase;

import java.util.*;

/**
 * Created by chientran on 9/29/15.
 */
public class PhraseScorer {
    Document document = null;

    Double B = 6.0;
    Double RHO = 0.5d;

    private static final String PARAGRAPH_SPLIT_REGEX = "(?m)(?=^\\s{4})";

    public PhraseScorer(Document document){
        this.document = document;
    }

    private Double weightingParagraph(Integer paragraphPosition){
        if (paragraphPosition < - Math.log(B) / Math.log(RHO)){
            return Math.pow(RHO, paragraphPosition) * B;
        }else{
            return 1.0d;
        }
    }

    public Double scorePhrase(Phrase phrase){
        Double score = 0.0d;
        Set<String> concepts = phrase.getConcepts();
        List<Paragraph> paragraphs = document.getParagraphs();
        int paragraphLength = paragraphs.size();

        for(String concept: concepts){
            for (int i=0; i<paragraphLength; i++){
                Integer count = paragraphs.get(i).countFrequency(concept);
                score += count * weightingParagraph(i);
            }
        }

        return score;
    }
}
