package com.coroutines.demo

import kotlinx.coroutines.*

class VenkatDemo {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            SynchronousCode8().main()
        }
    }
}
class ParallelInJava1 {
    private fun transform(n: Int): Int {
        try { Thread.sleep(1000) } catch (ignored: Exception) {}
        return n * 1
    }

    fun main() {
        val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        println(numbers.stream() //parallelStream instead of stream
                .mapToInt { e -> transform(e) }
                .reduce(0) { a, b -> Integer.sum(a, b) }
        )
    }
    //Java Streams - The structure of parallel code is the same as the structure of sequential code.
    //Kotlin Coroutines - The structure of asynchronous code is the same as the structure of synchronous code.
}

class Synchronous2 {
    fun task1() {
        println("Enter task1")
        println("Exit task1")
    }

    fun task2() {
        println("Enter task2")
        println("Exit task2")
    }

    fun main() {
        runBlocking {
            task1()
            task2()
            println("invoked tasks")
        }
    }
}

class Asynchronous3 {
    fun task1() {
        println("Enter task1")
        println("Exit task1")
    }

    fun task2() {
        println("Enter task2")
        println("Exit task2")
    }

    fun main() {
        runBlocking {
            launch { task1() }
            launch { task2() }
            println("invoked tasks")
        }
    }
}

class Concurrency4 {
    suspend fun task1() {
        println("Enter task1")
        yield() //Yields a value to the Iterator being built and suspends until the next value is requested
        println("Exit task1")
    }

    suspend fun task2() {
        println("Enter task2")
        yield()
        println("Exit task2")
    }

    fun main() {
        runBlocking {
            launch { task1() }
            launch { task2() }

            println("invoked tasks")
        }
    }
}

class ThinkCoroutines5 {
    val sequence = sequence {
        println("one")
        yield(1) //Yields a value to the [Iterator] being built and suspends until the next value is requested

        println("two")
        yield(2)

        println("three")
        yield(3)
    }

    fun main() {
        for (value in sequence) {
            println(value)
        }
    }
}

class WhatAboutThreads6 {
    fun doWork(n: Int) {
        println("doWork $n: called from ${Thread.currentThread()}")
    }

    fun main() {
        runBlocking {
            doWork(1)

            GlobalScope.launch {
                doWork(2)
                yield()
                doWork(3)
            }

            launch {
                doWork(4)
            }
        }
    }
}

class HowCoroutinesWorks7 {
    fun first(): Int {
        return 2
    }
    suspend fun second(): Int {
        return 2
    }
    fun main() {}

// ~/tools/kotlin-native-macos-1.3.31/bin/kotlinc src/main/kotlin/demo/HowCoroutinesWork07.kt
//kotlinc-jvm Sample.kt
//javap -c Sample

//   public final int first() {
//      return 2;
//   }
//    @Nullable
//    public final Object second(@NotNull Continuation $completion) {
//        return Boxing.boxInt(2);
//    }
}


suspend fun measureTime(block: suspend () -> Unit) {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime()
    println("${(end-start)/1.0e9} seconds")
}

suspend fun getIPAddress() = java.net.URL("https://api.ipify.org").readText()
suspend fun getResponse(code: String) = java.net.URL("http://httpstat.us/$code?sleep=2000").readText()

class SynchronousCode8 {
    fun main0() { //Get the ip address, if successful then get response from httpstat.us server
        runBlocking {
            val job = GlobalScope.launch {
                measureTime {
                    try {
                        println("getting IP Address")
                        val ip = getIPAddress()

                        try {
                            val code = "200" //if (Math.random() > 0.5) "200" else "404"
                            println("getting response")
                            println("request from ${ip} response: ${getResponse(code)}")
                        } catch (ex: Exception) {
                            println("Error getting response: $ex")
                        }
                    } catch (ex: Exception) {
                        println("Error getting IP: $ex")
                    }
                }
            }
            println("Started...")
            job.join()
        }
    }

    fun main() { //Get the ip address, if successful then get response from httpstat.us server
        runBlocking {
            val job = GlobalScope.launch {
                measureTime {
                    try {
                        println("getting IP Address")
                        val ip = async {  getIPAddress() }

                        try {
                            val code = "200" //if (Math.random() > 0.5) "200" else "404"
                            println("getting response")
                            println("request from ${ip.await()} response: ${getResponse(code)}")
                        } catch (ex: Exception) {
                            println("Error getting response: $ex")
                        }
                    } catch (ex: Exception) {
                        println("Error getting IP: $ex")
                    }
                }
            }
            println("Started...")
            job.join()
        }
    }
}

class Prices9 {
    fun getStockPrice(ticker: String) = java.net.URL("http://localhost:8080/price?ticker=$ticker").readText()

    val tickers = listOf("GOOG", "AMZN", "MSFT")

    fun main() {
        GlobalScope.launch {
            measureTime {
                val prices = mutableListOf<String>()
                for (ticker in tickers) {
                    prices += "Price for $ticker is ${getStockPrice(ticker)}" //turn to async {}
                }
                for (price in prices)
                    println(price)
            }
        }
        Thread.sleep(5000)
    }

    fun mainAsync() {
        GlobalScope.launch {
            measureTime {
                val prices = mutableListOf<Deferred<String>>()
                for (ticker in tickers) {
                    prices += async { "Price for $ticker is ${getStockPrice(ticker)}}" }
                }
                for (price in prices)
                    println(price.await())
            }
        }
        Thread.sleep(5000)
    }

}