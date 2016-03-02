package jaist.summarization;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Pair;
import intoxicant.analytics.coreNlp.StopwordAnnotator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by chientran on 2/27/16.
 */
public class StopwordRemover {
    public static List<CoreLabel> removeStopwords(List<CoreLabel> tokens){
        List<CoreLabel> filteredWords = new ArrayList<CoreLabel>();
        for(CoreLabel token: tokens){
            Pair<Boolean, Boolean> pair = token.get(StopwordAnnotator.class);
            if (!pair.first()){
                filteredWords.add(token);
            }
        }

        return filteredWords;
    }
}
