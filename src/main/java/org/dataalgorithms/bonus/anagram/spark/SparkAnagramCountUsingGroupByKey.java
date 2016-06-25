package org.dataalgorithms.bonus.anagram.spark;

// STEP-0: import required classes and interfaces
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
//
import scala.Tuple2;
//
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFlatMapFunction;
//
import org.apache.commons.lang.StringUtils;

/**
 * Find anagram counts for a given set of documents.
 * For example, if the sample input is comprised of 
 * the following 3 lines:
 * 
 *     Mary and Elvis lives in Detroit army Easter Listen 
 *     silent eaters Death Hated elvis Mary easter Silent
 *     Mary and Elvis are in army Listen Silent detroit
 * 
 * Then the output will be:
 *     
 *     Sorted     Anagrams and Frequencies
 *     =====   -> ========================
 *     (adeht  -> {death=1, hated=1})
 *     (eilnst -> {silent=3, listen=2})
 *     (eilsv  -> {lives=1, elvis=3})
 *     (aeerst -> {eaters=1, easter=2})
 *     (amry   -> {army=2, mary=3})
  * 
 * Since "in", "and", "are", "detroit" don't have an associated anagrams, 
 * they will be filtered out (dropped out):
 * 
 *     in -> null
 *     are -> null
 *     and -> null
 *     Detroit -> null
 *
 * @author Mahmoud Parsian
 *
 */
public class SparkAnagramCountUsingGroupByKey {

    public static void main(String[] args) throws Exception {

        // STEP-1: handle input parameters
        if (args.length != 3) {
            System.err.println("Usage: <N> <input-path> <output-path> ");
            System.exit(1);
        }

        // if a word.length < N, that word will be ignored
        final int N = Integer.parseInt(args[0]);
        System.out.println("args[0]: N=" + N);

        // identify I/O paths
        String inputPath = args[1];
        String outputPath = args[2];
        System.out.println("args[1]: <input-path>=" + inputPath);
        System.out.println("args[2]: <output-path>=" + outputPath);

        // STEP-2: create an instance of JavaSparkContext
        JavaSparkContext ctx = new JavaSparkContext();

        // STEP-3: create an RDD for input
        // input record format:
        //      word1 word2 word3 ...
        JavaRDD<String> lines = ctx.textFile(inputPath, 1);

        // STEP-4: create (K, V) pairs from input
        // K = sorted(word)
        // V = word
        JavaPairRDD<String, String> rdd = lines.flatMapToPair(
                new PairFlatMapFunction<String, String, String>() {
            @Override
            public Iterable<Tuple2<String, String>> call(String line) {
                if ((line == null) || (line.length() < N)) {
                    return Collections.EMPTY_LIST;
                }

                String[] words = StringUtils.split(line);
                if (words == null) {
                    return Collections.EMPTY_LIST;
                }

                List<Tuple2<String, String>> results = new ArrayList<Tuple2<String, String>>();
                for (String word : words) {
                    if (word.length() < N) {
                        // ignore strings with less than size N
                        continue;
                    }
                    if (word.matches(".*[,.;]$")) {
                        // remove the special char from the end
                        word = word.substring(0, word.length() - 1);
                    }
                    if (word.length() < N) {
                        // ignore strings with less than size N
                        continue;
                    }

                    String lowercaseWord = word.toLowerCase();
                    String sortedWord = sort(lowercaseWord);
                    results.add(new Tuple2<String, String>(sortedWord, lowercaseWord));
                }
                return results;
            }
        });

        // STEP-5: create anagrams
        // JavaPairRDD<String, Iterable<String>> anagrams = rdd.groupByKey();
        JavaPairRDD<String, Iterable<String>> anagramsList = rdd.groupByKey();

        // use mapValues() to find frequency of anagrams
        //mapValues[U](f: (V) => U): JavaPairRDD[K, U]
        // Pass each value in the key-value pair RDD through a map function without 
        // changing the keys; this also retains the original RDD's partitioning.
        JavaPairRDD<String, Map<String, Integer>> anagrams
                = anagramsList.mapValues(
                        new Function< 
                                     Iterable<String>,    // input
                                     Map<String, Integer> // output
                                    >() {
                    @Override
                    public Map<String, Integer> call(Iterable<String> values) {
                        Map<String, Integer> map = new HashMap<>();
                        for (String k : values) {
                            Integer frequency = map.get(k);
                            if (frequency == null) {
                                map.put(k, 1);
                            } else {
                                map.put(k, 1 + frequency);
                            }
                        }
                        return map;
                    }
                });
        
        //STEP-6: filter out the redundant RDD elements  
        //        
        // now we should filter (k,v) pairs from anagrams RDD:
        // where k is a "sorted word" and v is a Map<String,Integer>
        // if v.size() == 1 then it means that there is no associated
        // anagram for the diven "sorted word".
        //
        // For example our anagrams will have the following RDD entry:
        // (k=Detroit, v=Map.Entry("detroit", 2))
        // since the size of v (i.e., the hash map) is one that will 
        // be dropped out
        //
        // public JavaPairRDD<K,V> filter(Function<Tuple2<K,V>,Boolean> f)
        // Return a new RDD containing only the elements that satisfy a predicate;
        // If a counter (i.e., V) is 0, then exclude them 
        JavaPairRDD<String,Map<String, Integer>> filteredAnagrams = 
            anagrams.filter(new Function<Tuple2<String,Map<String, Integer>>,Boolean>() {
            @Override
            public Boolean call(Tuple2<String, Map<String, Integer>> entry) {
                 Map<String, Integer> map = entry._2;
                 if (map.size() > 1) {
                    return true; // include
                 }
                 else {
                    return false; // exclude
                 }
            }
        });
        
        

        // STEP-7: save output
        filteredAnagrams.saveAsTextFile(outputPath);

        // STEP-8: done
        ctx.close();
        System.exit(0);
    }

    static String sort8(String word) {
        String sorted = word.chars()
                .sorted()
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
        return sorted;
    }

    static String sort(String word) {
        char[] chars = word.toCharArray();
        Arrays.sort(chars);
        String sortedWord = String.valueOf(chars);
        return sortedWord;
    }

}
