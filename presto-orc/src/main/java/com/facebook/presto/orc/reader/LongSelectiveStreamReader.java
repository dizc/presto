/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.orc.reader;

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockLease;
import com.facebook.presto.common.predicate.TupleDomainFilter;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.orc.OrcAggregatedMemoryContext;
import com.facebook.presto.orc.StreamDescriptor;
import com.facebook.presto.orc.Stripe;
import com.facebook.presto.orc.metadata.ColumnEncoding;
import com.facebook.presto.orc.stream.InputStreamSources;
import com.google.common.io.Closer;
import org.openjdk.jol.info.ClassLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class LongSelectiveStreamReader
        implements SelectiveStreamReader
{
    private static final int INSTANCE_SIZE = ClassLayout.parseClass(LongSelectiveStreamReader.class).instanceSize();

    private final StreamDescriptor streamDescriptor;
    private final LongDirectSelectiveStreamReader directReader;
    private final LongDictionarySelectiveStreamReader dictionaryReader;
    private SelectiveStreamReader currentReader;

    public LongSelectiveStreamReader(
            StreamDescriptor streamDescriptor,
            Optional<TupleDomainFilter> filter,
            Optional<Type> outputType,
            OrcAggregatedMemoryContext systemMemoryContext,
            boolean isLowMemory)
    {
        this.streamDescriptor = requireNonNull(streamDescriptor, "streamDescriptor is null");
        directReader = new LongDirectSelectiveStreamReader(streamDescriptor, filter, outputType, systemMemoryContext.newOrcLocalMemoryContext(LongSelectiveStreamReader.class.getSimpleName()));
        dictionaryReader = new LongDictionarySelectiveStreamReader(
                streamDescriptor,
                filter,
                outputType,
                systemMemoryContext.newOrcLocalMemoryContext(LongSelectiveStreamReader.class.getSimpleName()),
                isLowMemory);
    }

    @Override
    public void startStripe(Stripe stripe)
            throws IOException
    {
        ColumnEncoding.ColumnEncodingKind kind = stripe.getColumnEncodings().get(streamDescriptor.getStreamId())
                .getColumnEncoding(streamDescriptor.getSequence())
                .getColumnEncodingKind();
        switch (kind) {
            case DIRECT:
            case DIRECT_V2:
            case DWRF_DIRECT:
                currentReader = directReader;
                break;
            case DICTIONARY:
                currentReader = dictionaryReader;
                break;
            default:
                throw new IllegalArgumentException("Unsupported encoding " + kind);
        }

        currentReader.startStripe(stripe);
    }

    @Override
    public void startRowGroup(InputStreamSources dataStreamSources)
            throws IOException
    {
        currentReader.startRowGroup(dataStreamSources);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .addValue(streamDescriptor)
                .toString();
    }

    @Override
    public void close()
    {
        try (Closer closer = Closer.create()) {
            closer.register(directReader::close);
            closer.register(dictionaryReader::close);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return INSTANCE_SIZE + directReader.getRetainedSizeInBytes() + dictionaryReader.getRetainedSizeInBytes();
    }

    @Override
    public int read(int offset, int[] positions, int positionCount)
            throws IOException
    {
        return currentReader.read(offset, positions, positionCount);
    }

    @Override
    public int[] getReadPositions()
    {
        return currentReader.getReadPositions();
    }

    @Override
    public Block getBlock(int[] positions, int positionCount)
    {
        return currentReader.getBlock(positions, positionCount);
    }

    @Override
    public BlockLease getBlockView(int[] positions, int positionCount)
    {
        return currentReader.getBlockView(positions, positionCount);
    }

    @Override
    public void throwAnyError(int[] positions, int positionCount)
    {
        currentReader.throwAnyError(positions, positionCount);
    }
}
