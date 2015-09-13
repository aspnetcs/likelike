# Requirements #

  * Java 1.6.0 or greater
  * Hadoop 0.20.0 or greater

# Download and install #
  * Install Java (install from [download page](http://www.java.com/download/))
  * Install Hadoop  (see [Hadoop Quick Start](http://hadoop.apache.org/common/docs/current/quickstart.html))
  * Install Likelike
    1. go to your home directory (/home/username if your account name is 'username')
```
     $ cd /home/username/
```
    1. download the latest the tar.gz file from the [donwnload page](http://code.google.com/p/likelike/downloads/list)
    1. extract the file
```
     $ tar zxvf likelike-*-*-*.tar.gz
     $ cd likelike-*-*
```

# Running examples #
Now let's run Likelike with a sample input file attached in the Likelike source (input\_sample/testInput.txt).

## Nearest neighbor extraction ##
Here I will describe the way to extract the similar (related) examples from data file. Here examples mean instances such as users in E-commerce sites or pictures for picture sharing sites).

First copy the sample input file into HDFS.

```
$ hadoop dfs -put input_sample/testInput.txt /blah/blah/path/
```

The sample input format have two columns, example id and the features. The two columns are split by tab and features are also split by spaces. If the data is past purchase data in some E-commerce sites, examples are the user ids and the features mean item ids bought by the user. The following is a fraction of the sample input.

```
$ hadoop dfs -cat /blah/blah/path/testInput.txt
0    776 2919 4576 4485 2380 4261 3622 1456 2109 3227
1    4358 4350 559 1136 2848 1345 1428 4212 2681 120
2    3614 3035 4592 1191 441 1408 734 1005 4826 33
3    691 878 1938 3381 127 4283 2269 3814 2908 1864
4    3781 603 1262 1988 1309 864 3511 729 1454 2845
5    3183 3575 180 4367 3616 4072 824 4976 4021 3361
6    3210 1956 2421 203 3006 2706 4794 4805 4180 2124
7    1548 3831 1447 3040 4137 3055 809 4497 2052 4994
...
```
The first line in the file tells us that the example 0 has features 776, 2219 etc.

Then, run likelike with the following parameters.

```
sh bin/likelike lsh -input /blah/blah/path/testInput.txt -output testOutputDir
```

We can see the result by the following command.

```
$ hadoop dfs -cat /blah/blah/path/testOutpuDir
0       5766
0       1962
0       2649
```

## Feature extraction from similar (related) examples ##

Now let's extract features from related examples. This function is useful to extract some recommendations for users of E-commerce sites.

To run this function, we use the result of nearest neighbor extraction. The extraction can be done with the following command.

```
sh bin/likelike featureExtraction -input /blah/blah/path/testOutputDir -feature /blah/blah/path/testInput.txt -output testOutputDir.features
```

We can see that the input file is the output from the lsh (nearest neighbor extraction) command, and after '-feature' parameter the input of the lsh command is also assigned.

Now let's see the result.

```
$ hadoop dfs -cat /blah/blah/path/testOutputDir.features
0       2201
0       3526
0       4882
0       277
0       2744
0       4269
0       129
0       678
0       2856
...
```

