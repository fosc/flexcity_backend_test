# Flexcity Backend technical test

## How to run the project
`./gradlew bootRun`

```bash
curl -X POST http://localhost:8080/assets \
-H "Content-Type: application/json" \
-d "{
\"date\": \"$(date +%F)\",
\"volume\": 100
}"
```

To change the selection algorithm, change `selection.engine` from `HYBRID` to `DP` or `GREEDY` in `application.properties`.
You can also change the asset provider configuration in `application.properties`:
```
# Asset Provider Configuration
# Number of assets to generate.
asset.provider.count=15000
# Total target volume (in kilowatts) across all generated assets.
asset.provider.total-kw-target=5000000
# Seed for random number generation, ensuring reproducible outputs.
asset.provider.seed=0
# Factor by which to scale the asset price.
asset.provider.base-price-factor=1.0
```


Availability of the asset data is distributed evenly between `LocalDate.now()`, `LocalDate.now().plusDays(1)`, and
`LocalDate.now().plusDays(-1)`. Choose one of these dates when testing to find available assets.


## Design Decisions
### Language and Framework
Springboot is widely used at Flexcity, providing a robust and modern web framework that scales well.
JVM is more performant than python, and Kotlin has the benefit of being a modern language.
The Java 21 platform running Kotlin 2.2.21 was chosen for stability and compatibility with Spring Boot.

### Architecture
The project was broken down into 4 parts:
1. The **controller**, which invokes the service and handles the presentation of the data.
2. The **asset service**, which provides the data to the controller and coordinates the business logic and algorithms. It has two dependencies (3 and 4):
3. The **asset provider**, which provides the data to the engine. This would be replaced by a database in the future.
4. The **asset selection engine**, which is an algorithm that chooses the cheapest subset of assets.

This design was chosen to leverage Springboot's dependency injection for the asset service, thereby making testing and
maintenance easier. It also facilitated the isolation of the business logic and the testing and comparison of different algorithms.

### Testing
Various unit tests and performance testing can be found under `src/test/kotlin`.
Automated data generation was used to achieve better coverage for the algorithms.
This same data generation is used in the asset provider to simulate real-world data.

## Assumptions
1. Several assumptions were made when generating the data:
   1. The combined kW across all assets available for a given TSO request may be about 1 to 3 GW currently, 
but will continue to increase overtime as flexcity integrates with more electrical consumers.
   2. The maximum avaiable kW represents the maximum requestable activation request volume.
      - As a result, we can ideally handle requests for at least 1 to 3 GW, and more overtime.
   2. The cost per kW is about 2 euros on average across all assets.
      - This is speculative. Ultimately, we cannot evaluate the cost very well without real data.
   3. Flexcity manages 10,000 sites. I estimate 3000 sites would be available per TSO, and that on average, each site has 5 assets. Thus, 3000 * 5 = 15,000 assets.
      - However, this number will increase over time, and we should be prepared to handle much larger numbers of assets.
      - For now, we will be satisfied with handling 30,000 assets at once.
   4. Approximately 1/3 of these assets are available on any given day.
      - This is speculative, but I would not expect all resources to be available at the same time.
      - We will nevertheless assume that most assets could be available on some days when doing our analysis.

2. Several assumptions were made regarding the nature of the request:
   1. The TSO request is sent before a critical state has been reached for the TSO.
      - Thus, we have at least ~3 seconds to react and run our algorithm.
   2. The TSO request may be sent multiple times per day.
   3. If the request volume exceeds the capacity of all available assets, we should return an error.
   4. The activation of an asset will finish on the same day that it was activated.
      - Otherwise, it is necessary to determine if a resource becomes unavailable during the scenario.
      - If this is a possible problem, we will need to add a duration to the request body.

## Trade-offs

Three algorithms were compared on a set of testing data with the following properties:

* The size of individual assets is scaled so that the entire list approximates at KW total value across all assets.
* Thus, increasing just the number of assets will result in smaller assets (fewer kw per asset)
* 50% of assets are small (10-100 kW before scaling).
* 45% are medium (100-1000 kW before scaling).
* 5% are large (1000-5000 kW before scaling).
* The asset price has +/- 50% uniformly distributed random noise.

Two algorithms were initially implemented and compared:

n = number of assets available
1. A greedy algorithm 
   - Select a subset of assets starting with the cheapest per kW until we exceed the target volume
   - Remove as many assets as possible without dropping below the target volume
2. A dynamic programming (DP) solution
   - Select the optimal (i.e. cheapest) subset of assets with sufficient volume.
   - This algorithm runtime is O(n*requested volume in kw)
3. A hybrid algorithm that combines the two algorithms
   - If the requested volume exceeds 100,000 kW, we used the greedy 
algorithm to select assets until only 50,000 kW needs to be chosen. 
   - The final 50,000 kW are selected with the DP algorithm.

### Algorithm Tradeoffs
n = number of assets available

| Algorithm | Runtime                     | Solution           | Performance for 1GW requested |
|:----------|:----------------------------|:-------------------|:------------------------------|
| Greedy    | O(n(log)n)                  | Not optimal        | Good                          |
| DP        | O(n*requested volume in kw) | Optimal            | Poor                          |
| Hybrid    | Composite                   | Better than Greedy | Good                          |

Thus, we are trading off the runtime of the greedy algorithm for the optimality of the DP algorithm.

#### Greedy algorithm tradeoff
As can be seen in the table below, under certain conditions the Greedy algorithm performs quite poorly.
The hybrid approach, however, is at least as good as the greedy algorithm and often better. 
```
========================================
Test Case: Poor greedy performance
========================================
Number of assets: 20
Total available volume: 99998 kW
Target volume: 50000 kW

Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |            7 |         12 |       50,072 |       36,673.65
Greedy          |            2 |          2 |       68,191 |       68,569.03
Hybrid          |           10 |         12 |       50,072 |       36,673.65
--------------------------------------------------------------------------------
```

#### Load Testing: Dynamic programming algorithm tradeoff
The limitations of the DP algorithm are seen in the load testing results. 
On my local machine (with `jvmArgs '-Xmx4g', '-Xms1g'`)
the DP algorithm runs out of memory when the request volume and/or list of assets grows beyond a cetain point.

In all these test cases the hybrid algorithm performs well: close to optimal cost optimization and quick enough runtime.
```
========================================
Test Case: Small - 100 assets, 5k target
========================================
Number of assets: 100
Total available volume: 49998 kW
Target volume: 5000 kW

Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |            6 |         15 |        5,000 |        2,816.82
Greedy          |            1 |         10 |        5,040 |        2,875.98
Hybrid          |            2 |         15 |        5,000 |        2,816.82
--------------------------------------------------------------------------------


========================================
Test Case: Medium - 1k assets, 50k target
========================================
Number of assets: 1000
Total available volume: 500009 kW
Target volume: 50000 kW

Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |          134 |         89 |       50,000 |       26,967.14
Greedy          |            1 |         88 |       50,001 |       26,974.69
Hybrid          |          105 |         89 |       50,000 |       26,967.14
--------------------------------------------------------------------------------


========================================
Test Case: Medium - 1k assets, 100k target
========================================
Number of assets: 1000
Total available volume: 500009 kW
Target volume: 100000 kW

Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |          305 |        198 |      100,000 |       58,040.78
Greedy          |            1 |        196 |      100,010 |       58,065.07
Hybrid          |          309 |        198 |      100,000 |       58,040.78
--------------------------------------------------------------------------------


========================================
Test Case: Medium - 3k assets, 100k target
========================================
Number of assets: 3000
Total available volume: 499992 kW
Target volume: 100000 kW

Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |          860 |        587 |      100,000 |       60,485.18
Greedy          |            3 |        587 |      100,000 |       60,486.70
Hybrid          |          983 |        587 |      100,000 |       60,485.18
--------------------------------------------------------------------------------


========================================
Test Case: Large - 3k assets, 500k target
========================================
Number of assets: 3000
Total available volume: 1000031 kW
Target volume: 500000 kW

actualDPTarget:49999
Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |        19836 |       1454 |      500,000 |      375,593.95
Greedy          |            2 |       1451 |      500,000 |      375,600.83
Hybrid          |          177 |       1454 |      500,000 |      375,593.95
--------------------------------------------------------------------------------


========================================
Test Case: Large - 3k assets, 1GW target
========================================
Number of assets: 3000
Total available volume: 5000005 kW
Target volume: 1000000 kW

actualDPTarget:49997
Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP              |        24567 |        582 |    1,000,000 |      604,783.70
Greedy          |            2 |        585 |    1,000,003 |      604,816.06
Hybrid          |          173 |        582 |    1,000,000 |      604,783.86
--------------------------------------------------------------------------------


========================================
Test Case: Large - 30k assets, 500k target
========================================
Number of assets: 30000
Total available volume: 10000150 kW
Target volume: 1000000 kW

actualDPTarget:50000
Results:
--------------------------------------------------------------------------------
Engine          |    Time (ms) |     Assets |  Volume (kW) |        Cost (€)
--------------------------------------------------------------------------------
DP - Out of Memory
Greedy          |            9 |       3135 |    1,000,002 |      553,850.75
Hybrid          |         1702 |       3133 |    1,000,000 |      553,848.60
--------------------------------------------------------------------------------
```

### Conclusion

If we want perfect accuracy, we should use the DP algorithm.
Otherwise, it is possible the hybrid algorithm may be good enough.

If we also need fast performance on large volumes (> 100,000kw), there are a few options to explore for improving the 
performance of the DP algorithm:
1. Precompute the DP table and store it in memory. This way we just have to look up the solution for a given target volume.
   - We would need to:
     - precompute after filtering, so for every day at least.
     - refresh every time available assets are updated or added.
     - lock the asset provider to prevent other threads reading during the update.
     - Make sure we do not miss updates
2. Explore ways to reduce memory usage of the DP algorithm and test with more memory.
   - It seems like the performance is maybe slowing down due to memory usage. If so, we can give it more memory.
   - We may be able to find other ways to reduce memory usage, such as using a more memory-efficient reconstruction step.

ToDo:
1. Clean up the HybridEngine code, which currently duplicates code from the other engines.
2. Analyze the DP algorithm with respect to memory usage and explore other ways to speed it up.
3. Look for counterexamples for when the hybrid algorithm is much worse than the DP algorithm
   (for the time being I have not found any)