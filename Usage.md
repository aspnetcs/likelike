# Input data format #
Likelike input files have the following two components split by tab,
  * instance id
  * features    - features for the instance. Each feature is split by tab

The following is the sample input.
```
0       776 2919 4576 4485 2380 4261 3622 1456 2109 3227
1       4358 4350 559 1136 2848 1345 1428 4212 2681 120
2       3614 3035 4592 1191 441 1408 734 1005 4826 33
3       691 878 1938 3381 127 4283 2269 3814 2908 1864
4       3781 603 1262 1988 1309 864 3511 729 1454 2845
5       3183 3575 180 4367 3616 4072 824 4976 4021 3361
6       3210 1956 2421 203 3006 2706 4794 4805 4180 2124
7       1548 3831 1447 3040 4137 3055 809 4497 2052 4994
8       4202 3124 1459 1838 3311 1236 4896 3525 1603 3083
9       2615 1741 4183 4395 959 3753 555 1160 306 2819
```

# Related (similar) instance extraction #
Extract related (or similar) examples.
```
bin/likelike lsh
    -input    INPUT           use INPUT as input resource
    -output   OUTPUT          use OUTPUT as outupt prefix
    [-depth   DEPTH]          use DEPTH as size of concatinations (default 1)
    [-iterate  ITERATE]       use ITERATE as the number of hash keys (default 1)
    [-maxRecommend  SIZE]     use SIZE as the maximum number of recommendation for one example
    [-help]                   show this message
```

# Feature Extraction from related examples #
Extract features from related examples.
```
bin/likelike featureExtraction  
    -input INPUT              use INPUT as input resource (output file of lsh)
    -output OUTPUT            use OUTPUT as outupt prefix
    -feature FEATURE          use feature as input resource (input file of lsh)
    [-help]                   show this message 
```

# Validation of the result fo Related instance extraction #
Validate the output from LSH.
```
bin/likelike validate
    -input INPUT              use INPUT as input resource (output file of lsh)
    -output OUTPUT            use OUTPUT as outupt prefix
    -feature FEATURE          use FEATURE as an input dir (input file of lsh)
    -threshold VALUE          use VALUE as the threshold  to output pairs
    [-help]                   show this message
```