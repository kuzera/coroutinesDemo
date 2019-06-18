import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.selects.select
import reactor.core.publisher.Mono
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.system.measureTimeMillis


//Hello world!
fun main0() {
    GlobalScope.launch { // launch a new coroutine in background and continue
        //delay is a special suspending function that does not block a thread, but suspends coroutine and it can be only used from a coroutine.
        delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
        println("Coroutines!") // print after delay
    }
    println("Hello,") // main thread continues while coroutine is delayed
    Thread.sleep(2000L) // block main thread for 2 seconds to keep JVM alive
}

//blocking and non-blocking worlds
fun main1() {
    GlobalScope.launch { // launch a new coroutine in background and continue
        delay(1000L)
        println("World!")
    }
    println("Hello,") // main thread continues here immediately
    runBlocking {     // but this expression blocks the main thread
        delay(2000L)  // ... while we delay for 2 seconds to keep JVM alive
    }
}

//the same, but all the code in blocking scope
fun main2() = runBlocking { // start main coroutine
    GlobalScope.launch { // launch a new coroutine in background and continue
        delay(1000L)
        println("World!")
    }
    println("Hello,") // main coroutine continues here immediately
    delay(2000L)      // delaying for 2 seconds to keep JVM alive
}

//Waiting for a job
//code of the main coroutine is not tied to the duration of the background job in any way. Much better
fun main3() = runBlocking {
    val job = GlobalScope.launch(start = CoroutineStart.LAZY) {
        // launch a new coroutine and keep a reference to its Job
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    job.join() // wait until child coroutine completes
}

//The same result, own instance of CoroutineScope, no need to manually join
//outer coroutine (runBlocking) does not complete until all the coroutines launched in its scope complete
fun main4() = runBlocking { // this: CoroutineScope
    launch { // launch a new coroutine in the scope of runBlocking
        delay(1000L)
        println("World!")
    }
    println("Hello,")
}

//own coroutineScope
//difference between runBlocking and coroutineScope is that the latter does not block the current thread while waiting for all children to complete
fun main5() = runBlocking { // this: CoroutineScope
    launch {
        delay(200L)
        println("Task from runBlocking")
    }

    coroutineScope { // Creates a coroutine scope
        launch {
            delay(500L)
            println("Task from nested launch")
        }

        delay(100L)
        println("Task from coroutine scope") // This line will be printed before the nested launch
    }

    println("Coroutine scope is over") // This line is not printed until the nested launch completes
}


fun main6() = runBlocking {
    launch { doWorld() }
    println("Hello,")
}
// this is your first suspending function
suspend fun doWorld() {
    delay(1000L)
    println("World!")
}

//Coroutines ARE light-weight - don't try this with new Thread()
fun main7() = runBlocking {
    repeat(100_000) { // launch a lot of coroutines
        launch {
            delay(1000L)
            print(".")
        }
    }
}

//Global coroutines are like daemon threads
fun main8() = runBlocking {
    GlobalScope.launch {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
//    println('a')
    delay(1300L) // just quit after delay
}

//cancelling a job
fun main9() = runBlocking {
    val job = launch {
        repeat(1000) { i ->
            println("job: I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancel() // cancels the job
    job.join() // waits for job's completion
    println("main: Now I can quit.")
}

//if a coroutine is working in a computation and does not check for cancellation, then it cannot be cancelled
//Coroutine cancellation is cooperative. A coroutine code has to cooperate to be cancellable.
//All the suspending functions in kotlinx.coroutines are cancellable
fun main10() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while (i < 10) { // computation loop, just wastes CPU
            // print a message twice a second
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}

//making a coroutine cancellable: by checking status or by yield()
fun main11() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while (isActive) { // cancellable computation loop
            // print a message twice a second
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}

//Closing resources with finally
//Cancellable suspending functions throw CancellationException on cancellation which can be handled in the usual way
fun main12() = runBlocking {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("job: I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            println("job: I'm running finally")
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}

//timeout handling
//withTimeout replaces manually tracking the reference to the corresponding Job and launch a separate coroutine to cancel the tracked one after delay
fun main13() = runBlocking {
    withTimeout(1300L) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    } // you may try {...} catch(TimeoutCancellationException ex) {}
}

//better timeout handling
//it returns null when timeout occurs
fun main14() = runBlocking {
    val result = withTimeoutOrNull(1300L) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
        "Done" // will get cancelled before it produces this result
    }
    println("Result is $result")
}

//Channels (experimental)

//Channel is conceptually very similar to BlockingQueue, but instead of a blocking put it has a suspending send, and instead of a blocking take it has a suspending receive.
fun main15() = runBlocking {
    val channel = Channel<Int>()
    launch {
        // this might be heavy CPU-consuming computation or async logic, we'll just send five squares
        for (x in 1..5) channel.send(x * x)
    }
    // here we print five received integers:
    repeat(5) { println(channel.receive()) }
    println("Done!")
}

//channel can be closed to indicate that no more elements are coming
fun main16() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        channel.close() // we're done sending
    }
    // here we print received values using `for` loop (until the channel is closed)
    for (y in channel) println(y)
    println("Done!")
}

//producer-consumer pattern
fun CoroutineScope.produceSquares(): ReceiveChannel<Int> = produce {
    for (x in 1..5) send(x * x)
}
fun main17() = runBlocking {
    val squares = produceSquares()
    squares.consumeEach { println(it) }
    println("Done!")
}

//pipeline is a pattern where one coroutine is producing, possibly infinite, stream of values
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) send(x++) // infinite stream of integers starting from 1
}
//another coroutine(s) are consuming that stream, doing some processing, and producing some other results
fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
    for (x in numbers) send(x * x)
}
//main code starts and connects the whole pipeline:
fun main18() = runBlocking {
    val numbers = produceNumbers() // produces integers from 1 and on
    val squares = square(numbers) // squares integers
    for (i in 1..5) println(squares.receive()) // print first five
    println("Done!") // we are done
    coroutineContext.cancelChildren() // cancel children coroutines
}


//Composing suspending functions
suspend fun doSomethingUsefulOne(): Int {
    delay(1000L) // pretend we are doing something useful here
    return 13
}
suspend fun doSomethingUsefulTwo(): Int {
    delay(1000L) // pretend we are doing something useful here, too
    return 29
}
//How to compute sum? Sequentially:
fun main19() = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        println("The answer is ${one + two}")
    }
    println("Completed in $time ms")
}
//Concurrently:
fun main20() = runBlocking<Unit> {
    val time = measureTimeMillis {
        //async returns a Deferred – a light-weight non-blocking future that represents a promise to provide a result later
        //use .await() on a deferred value to get its eventual result, but Deferred is also a Job, so you can cancel it if needed.
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}
//Lazily - starts coroutine only when its result is needed by some await or if a start function is invoked
fun main21() = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        // some computation
        one.start() // start the first one
        two.start() // start the second one
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}

//Async-style functions
//The result type is Deferred<Int>
fun somethingUsefulOneAsync() = GlobalScope.async {
    doSomethingUsefulOne()
}
fun somethingUsefulTwoAsync() = GlobalScope.async {
    doSomethingUsefulTwo()
}
// note that we don't have `runBlocking` to the right of `main` in this example
fun main22() {
    val time = measureTimeMillis {
        // we can initiate async actions outside of a coroutine
        val one = somethingUsefulOneAsync()
        val two = somethingUsefulTwoAsync()
        // but waiting for a result must involve either suspending or blocking.
        // here we use `runBlocking { ... }` to block the main thread while waiting for the result
        runBlocking {
            println("The answer is ${one.await() + two.await()}")
        }
    }
    println("Completed in $time ms")
}

//Structured concurrency with async
suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    //if something goes wrong inside the code of concurrentSum function and it throws an exception,
    // all the coroutines that were launched in its scope are cancelled
    one.await() + two.await()
}
suspend fun failedConcurrentSum(): Int = coroutineScope {
    val one = async<Int> {
        try { delay(Long.MAX_VALUE) // Emulates very long computation
            42
        } finally { println("First child was cancelled") }
    }
    val two = async<Int> {
        println("Second child throws an exception")
        throw ArithmeticException()
    }
    one.await() + two.await()
}
fun main23() = runBlocking<Unit> {
    val time = measureTimeMillis {
        println("The answer is ${concurrentSum()}")
//        println("The answer is ${failedConcurrentSum()}")
    }
    println("Completed in $time ms")
}


//Coroutines always execute in some CoroutineContext which consists of:
// - Job of the coroutine
// - dispatcher - determines what thread(s) the coroutine uses for its execution
//Dispatcher can:
// - confine coroutine execution to a specific thread,
// - dispatch it to a thread pool
// - let it run unconfined.
fun main24() = runBlocking<Unit> {
    launch { // inherits context of the parent, main runBlocking coroutine
        println("main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
    }
    //The Dispatchers.Unconfined coroutine dispatcher starts coroutine in the caller thread, but only until the first suspension point.
    // After suspension it resumes in the thread that is fully determined by the suspending function that was invoked.
    // Unconfined dispatcher is appropriate when coroutine does not consume CPU time nor updates any shared data (like UI) that is confined to a specific thread.
    launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
        println("Unconfined            : I'm working in thread ${Thread.currentThread().name}")
    }
    //uses shared background pool of threads, so launch(Dispatchers.Default) { ... } uses the same dispatcher as GlobalScope.launch { ... }
    launch(Dispatchers.Default) { // will get dispatched to DefaultDispatcher
        println("Default               : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(newSingleThreadContext("MyOwnThread")) { // will get its own new thread
        println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
    }

    val customDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    launch(customDispatcher) { // executor needs to be manually closed
        println("customDispatcher: I'm working in thread ${Thread.currentThread().name}")
    }
    (customDispatcher.executor as ExecutorService).shutdown()
}

//On the other side, by default, a dispatcher for the outer CoroutineScope is inherited.
// The default dispatcher for runBlocking coroutine, in particular, is confined to the invoker thread,
// so inheriting it has the effect of confining execution to this thread with a predictable FIFO scheduling
fun main25() = runBlocking<Unit> {
    launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
        println("Unconfined      : I'm working in thread ${Thread.currentThread().name}")
        delay(500)
        println("Unconfined      : After delay in thread ${Thread.currentThread().name}")
    }
    launch { // context of the parent, main runBlocking coroutine
        println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
        delay(1000)
        println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
    }
}

//Debugging coroutines and threads, run with -Dkotlinx.coroutines.debug
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")
fun main26() = runBlocking<Unit> {
    val a = async {
        log("I'm computing a piece of the answer")
        6
    }
    val b = async {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
}

//Job in the context
fun main27() = runBlocking<Unit> {
    println("My job is ${coroutineContext[Job]}")
}

//Children of a coroutine

//When a coroutine is launched in the CoroutineScope of another coroutine, it inherits its context via CoroutineScope.coroutineContext
// and the Job of the new coroutine becomes a child of the parent coroutine's job.
// When the parent coroutine is cancelled, all its children are recursively cancelled, too
fun main28() = runBlocking<Unit> {
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        // it spawns two other jobs, one with GlobalScope
        GlobalScope.launch {
            println("job1: I run in GlobalScope and execute independently!")
            delay(1000)
            println("job1: I am not affected by cancellation of the request")
        }
        // and the other inherits the parent context
        launch {
            delay(100)
            println("job2: I am a child of the request coroutine")
            delay(1000)
            println("job2: I will not execute this line if my parent request is cancelled")
        }
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("main: Who has survived request cancellation?")
}

//Naming coroutines for debugging
fun main29() = runBlocking<Unit> {
    log("Started main coroutine")
    // run two background value computations
    val v1 = async(CoroutineName("v1coroutine")) {
        delay(500)
        log("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2coroutine")) {
        delay(1000)
        log("Computing v2")
        6
    }
    log("The answer for v1 / v2 = ${v1.await() / v2.await()}")
}

//Combining context elements
//Sometimes we need to define multiple elements for coroutine context. We can use + operator for that.
// For example, we can launch a coroutine with an explicitly specified dispatcher and an explicitly specified name at the same time
fun main30() = runBlocking<Unit> {
    launch(Dispatchers.Default + CoroutineName("test")) {
        println("I'm working in thread ${Thread.currentThread().name}")
    }
}

//CoroutineScope is tied to the lifecycle of our activity. CoroutineScope instance can be created by CoroutineScope() or MainScope() factory functions.
// The former creates a general-purpose scope, while the latter creates scope for UI applications and uses Dispatchers.Main as default dispatcher:
class Activity : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    fun destroy() {
        cancel() // Extension on CoroutineScope
    }
    // to be continued ...

    // class Activity continues
    fun doSomething() {
        // launch ten coroutines for a demo, each working for a different time
        repeat(10) { i ->
            launch {
                delay((i + 1) * 200L) // variable delay 200ms, 400ms, ... etc
                println("Coroutine $i is done")
            }
        }
    }
} // class Activity ends
fun main31() = runBlocking<Unit> {
    val activity = Activity()
    activity.doSomething() // run test function
    println("Launched coroutines")
    delay(500L) // delay for half a second
    println("Destroying activity!")
    activity.destroy() // cancels all coroutines
    delay(1000) // visually confirm that they don't work
}

//Thread-local data
//for coroutines, which are not bound to any particular thread, it is hard to achieve it manually without writing a lot of boilerplate
//For ThreadLocal, asContextElement extension function is here for the rescue.
// It creates an additional context element, which keeps the value of the given ThreadLocal and restores it every time the coroutine switches its context.
val threadLocal = ThreadLocal<String?>() // declare thread-local variable
fun main32() = runBlocking<Unit> {
    threadLocal.set("main")
    println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch")) {
        println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
        yield()
        println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    }
    job.join()
    println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
}

//Exception propagation
//Coroutine builders come in two flavors:
// - propagating exceptions automatically (launch and actor) - treat exceptions as unhandled, similar to Java's Thread.uncaughtExceptionHandler
// - or exposing them to users (async and produce) - relying on the user to consume the final exception, for example via await or receive
fun main33() = runBlocking {
    val job = GlobalScope.launch {
        println("Throwing exception from launch")
        throw IndexOutOfBoundsException() // Will be printed to the console by Thread.defaultUncaughtExceptionHandler
    }
    job.join()
    println("Joined failed job")
    val deferred = GlobalScope.async {
        println("Throwing exception from async")
        throw ArithmeticException() // Nothing is printed, relying on user to call await
    }
    try {
        deferred.await()
        println("Unreached")
    } catch (e: ArithmeticException) {
        println("Caught ArithmeticException")
    }
}

//CoroutineExceptionHandler is invoked only on exceptions which are not expected to be handled by the user,
// so registering it in async builder and the like of it has no effect.
fun main34() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("Caught $exception")
    }
    val job = GlobalScope.launch(handler) {
        throw AssertionError()
    }
    val deferred = GlobalScope.async(handler) {
        throw ArithmeticException() // Nothing will be printed, relying on user to call deferred.await()
    }
    joinAll(job, deferred)
}

//Cancellation and exceptions
//Coroutines internally use CancellationException for cancellation, these exceptions are ignored by all handlers,
// so they should be used only as the source of additional debug information, which can be obtained by catch block.
// When a coroutine is cancelled using Job.cancel without a cause, it terminates, but it does not cancel its parent.
// Cancelling without cause is a mechanism for parent to cancel its children without cancelling itself
fun main35() = runBlocking {
    val job = launch {
        val child = launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                println("Child is cancelled")
            }
        }
        yield()
        println("Cancelling child")
        child.cancel()
        child.join()
        yield()
        println("Parent is not cancelled")
    }
    job.join()
}

//CoroutineExceptionHandler is always installed to a coroutine that is created in GlobalScope.
// It does not make sense to install an exception handler to a coroutine that is launched in the scope of the main runBlocking,
// since the main coroutine is going to be always cancelled when its child completes with exception despite the installed handler.
fun main36() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("Caught $exception")
    }
    val job = GlobalScope.launch(handler) {
        launch { // the first child
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    println("Children are cancelled, but exception is not handled until all children terminate")
                    delay(100)
                    println("The first child finished its non cancellable block")
                }
            }
        }
        launch { // the second child
            delay(10)
            println("Second child throws an exception")
            throw ArithmeticException()
        }
    }
    job.join()
}

//Exceptions aggregation
//if multiple children of a coroutine throw an exception? The general rule is "the first exception wins",
// so the first thrown exception is exposed to the handler. But that may cause lost exceptions,
// for example if coroutine throws an exception in its finally block. So, additional exceptions are suppressed
fun main37() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("Caught $exception with suppressed ${exception.suppressed.contentToString()}")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                throw ArithmeticException()
            }
        }
        launch {
            delay(100)
            throw IOException()
        }
        delay(Long.MAX_VALUE)
    }
    job.join()
}


//Supervision job
//A good example of such a requirement is a UI component with the job defined in its scope.
// If any of the UI's child tasks have failed, it is not always necessary to cancel (effectively kill) the whole UI component,
// but if UI component is destroyed (and its job is cancelled), then it is necessary to fail all child jobs as their results are no longer required.
//Another example is a server process that spawns several children jobs and needs to supervise their execution,
// tracking their failures and restarting just those children jobs that had failed.
fun main38() = runBlocking {
    val supervisor = SupervisorJob()
    with(CoroutineScope(coroutineContext + supervisor)) {
        // launch the first child -- its exception is ignored for this example (don't do this in practice!)
        val firstChild = launch(CoroutineExceptionHandler { _, _ ->  }) {
            println("First child is failing")
            throw AssertionError("First child is cancelled")
        }
        // launch the second child
        val secondChild = launch {
            firstChild.join()
            // Cancellation of the first child is not propagated to the second child
            println("First child is cancelled: ${firstChild.isCancelled}, but second one is still active")
            try {
                delay(Long.MAX_VALUE)
            } finally {
                // But cancellation of the supervisor is propagated
                println("Second child is cancelled because supervisor is cancelled")
            }
        }
        // wait until the first child fails & completes
        firstChild.join()
        println("Cancelling supervisor")
        supervisor.cancel()
        secondChild.join()
    }
}

//Supervision scope
//- propagates cancellation only in one direction and cancels all children only if it has failed itself.
// It also waits for all children before completion just like coroutineScope does.
fun main39() = runBlocking {
    try {
        supervisorScope {
            val child = launch {
                try {
                    println("Child is sleeping")
                    delay(Long.MAX_VALUE)
                } finally {
                    println("Child is cancelled")
                }
            }
            // Give our child a chance to execute and print using yield
            yield()
            println("Throwing exception from scope")
            throw AssertionError()
        }
    } catch(e: AssertionError) {
        println("Caught assertion error")
    }
}

//Exceptions in supervised coroutines
//Another crucial difference between regular and supervisor jobs is exception handling.
// Every child should handle its exceptions by itself via exception handling mechanisms.
// This difference comes from the fact that child's failure is not propagated to the parent
fun main40() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("Caught $exception")
    }
    supervisorScope {
        val child = launch(handler) {
            println("Child throws an exception")
            throw AssertionError()
        }
        println("Scope is completing")
    }
    println("Scope is completed")
}


//Select expression (experimental)
//-await multiple suspending functions simultaneously and select the first one that becomes available.
fun CoroutineScope.fizz() = produce<String> {
    while (true) { // sends "Fizz" every 300 ms
        delay(300)
        send("Fizz")
    }
}
fun CoroutineScope.buzz() = produce<String> {
    while (true) { // sends "Buzz!" every 500 ms
        delay(500)
        send("Buzz!")
    }
}
suspend fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) {
    //Using receive suspending function we can receive either from one channel or the other.
    //But select expression allows us to receive from both simultaneously using its onReceive clauses:
    select<Unit> { // <Unit> means that this select expression does not produce any result
        fizz.onReceive { value ->  // this is the first select clause
            println("fizz -> '$value'")
        }
        buzz.onReceive { value ->  // this is the second select clause
            println("buzz -> '$value'")
        }
    }
}
fun main41() = runBlocking<Unit> {
    val fizz = fizz()
    val buzz = buzz()
    repeat(7) {
        selectFizzBuzz(fizz, buzz)
    }
    coroutineContext.cancelChildren() // cancel fizz & buzz coroutines
}


//Shared mutable state and concurrency
suspend fun CoroutineScope.massiveRun(action: suspend () -> Unit) {
    val n = 100  // number of coroutines to launch
    val k = 1000 // times an action is repeated by each coroutine
    val time = measureTimeMillis {
        val jobs = List(n) {
            launch {
                repeat(k) { action() }
            }
        }
        jobs.forEach { it.join() }
    }
    println("Completed ${n * k} actions in $time ms")
}
var counter = 0
fun main42() = runBlocking<Unit> {
    GlobalScope.massiveRun {
        counter++
    }
    println("Counter = $counter")
    //unlikely to ever print "Counter = 100000", because a thousand coroutines increment the counter concurrently from multiple threads without any synchronization
}
//first solution is to select thread-safe data type
var counterAtomic = AtomicInteger()
fun main43() = runBlocking<Unit> {
    GlobalScope.massiveRun {
        counterAtomic.incrementAndGet()
    }
    println("Counter = ${counterAtomic.get()}")
}

//Thread confinement fine-grained
//- approach to the problem of shared mutable state where all access to the particular shared state is confined to a single thread.
// It is typically used in UI applications, where all UI state is confined to the single event-dispatch/application thread.
// It is easy to apply with coroutines by using a single-threaded context.
val counterContext = newSingleThreadContext("CounterContext")
var counter1 = 0
fun main44() = runBlocking<Unit> {
    GlobalScope.massiveRun { // run each coroutine with DefaultDispathcer
        withContext(counterContext) { // but confine each increment to the single-threaded context
            counter1++
        }
    }
    println("Counter1 = $counter1")
}






//https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md - experimental
//            @Test
//            fun testFoo() = runBlockingTest { // a coroutine with an extra test control
//                val actual = foo()
//                // ...
//            }
//            suspend fun foo() {
//                delay(1_000) // auto-advances without delay due to runBlockingTest
//                // ...
//            }


var continuation: Continuation<String>? = null
//@ExperimentalCoroutinesApi
fun main999() {
    val job = GlobalScope.launch(Dispatchers.Unconfined) {
        while (true) {
            println(suspendHere())
        }
    }
    continuation!!.resume("Resumed first time")
    continuation!!.resume("Resumed second time")
}
suspend fun suspendHere() = suspendCancellableCoroutine<String> {
    continuation = it
}

    class Solutions(override val coroutineContext: CoroutineContext) : CoroutineScope {

        fun monoClassicalExample(): Mono<Int> {
            return Mono.just<Int>(1)
        }

        //Module kotlinx-coroutines-reactor
//Coroutine builders:
//mono - Cold mono that starts coroutine on subscribe
//flux - Cold flux that starts coroutine on subscribe
//Cold publisher - (e.g. just()) generates data for each subscription. If no subscription is created, then data never gets generated.
//e.g. think of an HTTP request: Each new subscriber will trigger an HTTP call, but no call is made if no one is interested in the result.
//Hot publisher - (e.g. defer()) might start publishing data right away and would continue doing so whenever a new Subscriber comes in
        fun main100() = mono {
            val one = doSomethingUsefulOne()
            val two = async { doSomethingUsefulTwo() }
            val three = monoClassicalExample()
            one + two.await() + three.awaitFirst()
        }


        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                val mono = Solutions(EmptyCoroutineContext).main100()
                println("Mono=${mono.block()}")
            }
        }
    }
