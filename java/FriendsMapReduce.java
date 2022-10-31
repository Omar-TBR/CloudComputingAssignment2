import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class FriendsMapReduce extends Configured implements Tool {

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new Configuration(), new FriendsMapReduce(), args);
    }


    @Override
    public int run(String[] args) throws Exception {
        Job friendJob = Job.getInstance(getConf(), "Friend map reduce JOB");
        friendJob.setJarByClass(FriendsMapReduce.class);
        friendJob.setMapOutputKeyClass(Text.class);
        friendJob.setMapOutputValueClass(FriendConnectionWritable.class);
        friendJob.setMapperClass(FriendsMap.class);
        friendJob.setReducerClass(RecommendedFriendsReduce.class);
        friendJob.setInputFormatClass(KeyValueTextInputFormat.class);
        friendJob.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.addInputPath(friendJob, new Path(args[0]));
        FileOutputFormat.setOutputPath(friendJob, new Path(args[1]));

        friendJob.waitForCompletion(true);

        return 0;
    }

    public static class FriendsMap extends Mapper<Text, Text, Text, FriendConnectionWritable> {
        @Override
        protected void map(Text key, Text value, Mapper<Text, Text, Text, FriendConnectionWritable>.Context context) throws IOException, InterruptedException {
            if (!value.toString().isEmpty()) {
                Integer[] friendArray = Stream.of(value.toString().split(",")).map(Integer::valueOf).toArray(Integer[]::new);
                for (int indexOfDirectFriend = 0; indexOfDirectFriend < friendArray.length; indexOfDirectFriend++) {
                    context.write(key, new FriendConnectionWritable(new IntWritable(friendArray[indexOfDirectFriend]), new BooleanWritable(true)));
                    for (int indexOfOtherDirectFriend = indexOfDirectFriend + 1; indexOfOtherDirectFriend < friendArray.length; indexOfOtherDirectFriend++) {
                        context.write(new Text(friendArray[indexOfDirectFriend].toString()), new FriendConnectionWritable(new IntWritable(friendArray[indexOfOtherDirectFriend]), new BooleanWritable(false)));
                        context.write(new Text(friendArray[indexOfOtherDirectFriend].toString()), new FriendConnectionWritable(new IntWritable(friendArray[indexOfDirectFriend]), new BooleanWritable(false)));
                    }
                }
            }
        }
    }

    public static class RecommendedFriendsReduce extends Reducer<Text, FriendConnectionWritable, Text, Text> {
        @Override
        protected void reduce(Text key, Iterable<FriendConnectionWritable> values, Reducer<Text, FriendConnectionWritable, Text, Text>.Context context) throws IOException, InterruptedException {
            TreeMap<Integer, Integer> friendOfFriendCounter = new TreeMap<>();
            HashSet<Integer> directFriends = new HashSet<>();
            for (FriendConnectionWritable fr : values) {
                Integer connectionId = fr.getConnectionId().get();
                if (fr.isDirectFriend().get()) {
                    directFriends.add(connectionId);
                } else {
                    if (friendOfFriendCounter.containsKey(connectionId)) {
                        if (!directFriends.contains(connectionId)) {
                            friendOfFriendCounter.put(connectionId, friendOfFriendCounter.get(connectionId) + 1);
                        }
                    } else {
                        friendOfFriendCounter.put(connectionId, 1);
                    }
                }
            }

            LinkedHashMap<Integer, Integer> topTenFriendOfFriendCounter =
                    friendOfFriendCounter.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                            .limit(10)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            List<String> topTenFriendOfFriendIdList = topTenFriendOfFriendCounter.keySet().stream().map(Object::toString)
                    .collect(Collectors.toList());

            context.write(key, new Text(String.join(",", topTenFriendOfFriendIdList)));
        }
    }

    public static class FriendConnectionWritable implements Writable {

        private final IntWritable connectionId;

        public IntWritable getConnectionId() {
            return connectionId;
        }

        public BooleanWritable isDirectFriend() {
            return directFriend;
        }

        private final BooleanWritable directFriend;

        public FriendConnectionWritable() {
            this.connectionId = new IntWritable();
            this.directFriend = new BooleanWritable();
        }

        public FriendConnectionWritable(IntWritable connectionId, BooleanWritable directFriend) {
            this.connectionId = connectionId;
            this.directFriend = directFriend;
        }

        @Override
        public void readFields(DataInput dataInput) throws IOException {
            this.connectionId.readFields(dataInput);
            this.directFriend.readFields(dataInput);
        }

        @Override
        public void write(DataOutput dataOutput) throws IOException {
            this.connectionId.write(dataOutput);
            this.directFriend.write(dataOutput);
        }
    }
}

