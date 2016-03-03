package jaist.summarization.utils;

import edu.stanford.nlp.util.CollectionUtils;

import java.util.*;

/**
 * Created by chientran on 3/3/16.
 */
public class StringUtils {
    public static int countWords(String text){
        return splitStringToWords(text).size();
    }

    public static List<String> generateUnigrams(String text){
        return splitStringToWords(text);
    }

    public static List<String> generateBigrams(List<String> unigrams){
        List<List<String>> bigramTokens = CollectionUtils.getNGrams(unigrams, 2, 2);
        List<String> bigrams = new ArrayList<>();
        for(List<String> tokens: bigramTokens){
            bigrams.add(String.join(" ", tokens));
        }

        return bigrams;
    }

    private static List<String> splitStringToWords(String text){
        return Arrays.asList(text.split("\\W+"));
    }
}
