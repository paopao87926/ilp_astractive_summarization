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
    Annotation document = null;

    ArrayList<Paragraph> paragraphs = null;

    Double B = 6.0;
    Double RHO = 0.5d;

    private static final String PARAGRAPH_SPLIT_REGEX = "(?m)(?=^\\s{4})";

    public PhraseScorer(Annotation document){
        this.document = document;
        paragraphs = new ArrayList<>();

        separateDocumentIntoParagraphs();
    }

    private void separateDocumentIntoParagraphs(){
        String text = this.document.toString();

        String[] paragraphTexts = text.split(PARAGRAPH_SPLIT_REGEX);

        for (String paragraphText: paragraphTexts){
            Paragraph paragraph = new Paragraph(paragraphText);

            paragraphs.add(paragraph);
        }

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
        HashSet<String> concepts = phrase.getConcepts();
        for(String concept: concepts){
            for (int i=0; i<paragraphs.size(); i++){
                Integer count = paragraphs.get(i).countFrequency(concept);
                score += count * weightingParagraph(i);
            }
        }

        return score;
    }
}
