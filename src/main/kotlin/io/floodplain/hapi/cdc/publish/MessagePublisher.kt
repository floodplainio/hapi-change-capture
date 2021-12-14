package io.floodplain.hapi.cdc.publish

class Message(val topic: String, val key: String, val body: ByteArray?)

interface MessagePublisher {
    fun updateCount(): Long
    fun deleteCount(): Long

    /**
     * Publishes a single message and blocks until confirmed.
     */
    fun publish(topic: String, key: String, payload: ByteArray)

    /**
     * Publishes a list of messages and blocks until confirmed.
     */

    // Performance optimized, will post all messages before blocking on confirmation
    fun publishAll(messages: List<Message>)
    fun delete(topic: String, key: String)
}
