/*
 *  Copyright (C) 2022 Abhijith Shivaswamy
 *   See the notice.md file distributed with this work for additional
 *   information regarding copyright ownership.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.github.crackthecodeabhi.kreds.connection

import io.github.crackthecodeabhi.kreds.messages.ReentrantMutexContextKey
import io.github.crackthecodeabhi.kreds.args.Argument
import io.github.crackthecodeabhi.kreds.commands.BlockingListCommands
import io.github.crackthecodeabhi.kreds.commands.BlockingZSetCommands
import io.github.crackthecodeabhi.kreds.commands.Command
import io.github.crackthecodeabhi.kreds.commands.CommandExecution
import io.github.crackthecodeabhi.kreds.commands.ConnectionCommands
import io.github.crackthecodeabhi.kreds.commands.ConnectionCommandsExecutor
import io.github.crackthecodeabhi.kreds.commands.FunctionCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.FunctionCommands
import io.github.crackthecodeabhi.kreds.commands.HashCommands
import io.github.crackthecodeabhi.kreds.commands.HashCommandsExecutor
import io.github.crackthecodeabhi.kreds.commands.HyperLogLogCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.HyperLogLogCommands
import io.github.crackthecodeabhi.kreds.commands.JsonCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.JsonCommands
import io.github.crackthecodeabhi.kreds.commands.KeyCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.KeyCommands
import io.github.crackthecodeabhi.kreds.commands.ListCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.ListCommands
import io.github.crackthecodeabhi.kreds.commands.ScriptingCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.ScriptingCommands
import io.github.crackthecodeabhi.kreds.commands.ServerCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.ServerCommands
import io.github.crackthecodeabhi.kreds.commands.SetCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.SetCommands
import io.github.crackthecodeabhi.kreds.commands.StringCommands
import io.github.crackthecodeabhi.kreds.commands.StringCommandsExecutor
import io.github.crackthecodeabhi.kreds.commands.ZSetCommandExecutor
import io.github.crackthecodeabhi.kreds.commands.ZSetCommands
import io.github.crackthecodeabhi.kreds.pipeline.Pipeline
import io.github.crackthecodeabhi.kreds.pipeline.PipelineImpl
import io.github.crackthecodeabhi.kreds.pipeline.Transaction
import io.github.crackthecodeabhi.kreds.pipeline.TransactionImpl
import io.github.crackthecodeabhi.kreds.protocol.CommandExecutor
import io.github.crackthecodeabhi.kreds.protocol.ICommandProcessor
import io.github.crackthecodeabhi.kreds.messages.RedisMessage
import io.github.crackthecodeabhi.kreds.messages.withReentrantLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalStdlibApi::class)
public interface KredsClient : AutoCloseable, KeyCommands, StringCommands, ConnectionCommands, PublisherCommands,
    HashCommands, SetCommands, ListCommands, HyperLogLogCommands, ServerCommands, ZSetCommands, JsonCommands,
    ScriptingCommands, FunctionCommands {
    public fun pipelined(): Pipeline
    public fun transaction(): Transaction
}

internal interface InternalKredsClient : KredsClient {
    val endpoint: Endpoint
}

@OptIn(ExperimentalStdlibApi::class)
public interface BlockingKredsClient : AutoCloseable, BlockingListCommands, BlockingZSetCommands

internal abstract class AbstractKredsClient(
    endpoint: Endpoint,
    config: KredsClientConfig
) :
    KonnectionImpl(endpoint, config), CommandExecutor {

    override suspend fun <T> execute(command: Command, processor: ICommandProcessor<T>, vararg args: Argument): T =
        withReentrantLock {
            connectWriteAndFlush(processor.encode(command, *args))
            processor.decode(read())
        }

    override suspend fun <T> execute(commandExecution: CommandExecution<T>): T = withReentrantLock {
        with(commandExecution) {
            connectWriteAndFlush(processor.encode(command, *args))
            processor.decode(read())
        }
    }

    override suspend fun executeCommands(commands: List<CommandExecution<*>>): List<RedisMessage> = withReentrantLock {
        connect()
        commands.forEach {
            with(it) {
                write(processor.encode(command, *args))
            }
        }
        flush()
        // collect the response messages.
        val responseList = mutableListOf<RedisMessage>()
        repeat(commands.size) {
            responseList.add(it, read())
        }
        responseList
    }
}

internal class DefaultKredsClient(
    override val endpoint: Endpoint,
    config: KredsClientConfig
) :
    AbstractKredsClient(endpoint, config), KredsClient, InternalKredsClient, KeyCommandExecutor,
    StringCommandsExecutor, ConnectionCommandsExecutor, PublishCommandExecutor, HashCommandsExecutor,
    SetCommandExecutor, ListCommandExecutor, HyperLogLogCommandExecutor, ServerCommandExecutor, BlockingKredsClient,
    ZSetCommandExecutor, JsonCommandExecutor, ScriptingCommandExecutor, FunctionCommandExecutor {

    override val mutex: Mutex = Mutex()

    override val key: ReentrantMutexContextKey = ReentrantMutexContextKey(mutex)

    override fun pipelined(): Pipeline = PipelineImpl(this)

    override fun transaction(): Transaction = TransactionImpl(this)

    override fun close() {
        runBlocking {
            disconnect()
        }
    }
}
