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
package com.facebook.presto.cost;

import com.facebook.presto.spi.statistics.EmptyPlanStatisticsProvider;
import com.facebook.presto.spi.statistics.HistoryBasedPlanStatisticsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import static java.util.Objects.requireNonNull;

public class HistoryBasedPlanStatisticsManager
{
    private final ObjectMapper objectMapper;

    private HistoryBasedPlanStatisticsProvider historyBasedPlanStatisticsProvider = EmptyPlanStatisticsProvider.getInstance();
    private boolean statisticsProviderAdded;

    @Inject
    public HistoryBasedPlanStatisticsManager(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    public void addHistoryBasedPlanStatisticsProviderFactory(HistoryBasedPlanStatisticsProvider historyBasedPlanStatisticsProvider)
    {
        if (statisticsProviderAdded) {
            throw new IllegalStateException("historyBasedPlanStatisticsProvider can only be set once");
        }
        this.historyBasedPlanStatisticsProvider = historyBasedPlanStatisticsProvider;
        statisticsProviderAdded = true;
    }

    public HistoryBasedPlanStatisticsCalculator getHistoryBasedPlanStatisticsCalculator(StatsCalculator delegate)
    {
        return new HistoryBasedPlanStatisticsCalculator(() -> historyBasedPlanStatisticsProvider, delegate, objectMapper);
    }
}
