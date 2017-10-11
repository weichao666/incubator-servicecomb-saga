/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.saga.core;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.servicecomb.saga.core.dag.Node;

class CompensationTaskConsumer implements TaskConsumer {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Map<String, SagaTask> tasks;
  private final Map<String, SagaResponse> completedTransactions;

  CompensationTaskConsumer(Map<String, SagaTask> tasks, Map<String, SagaResponse> completedTransactions) {
    this.tasks = tasks;
    this.completedTransactions = completedTransactions;
  }

  @Override
  public SagaResponse consume(Collection<Node<SagaRequest>> nodes, SagaResponse previousResponse) {
    List<SagaResponse> responses = new ArrayList<>(nodes.size());
    for (Node<SagaRequest> node : nodes) {
      SagaRequest request = node.value();

      if (completedTransactions.containsKey(request.id())) {
        log.info("Starting request {} id={}", request.serviceName(), request.id());
        responses.add(tasks.get(request.task()).compensate(request));
        log.info("Completed request {} id={}", request.serviceName(), request.id());
      }
    }
    return responseOf(responses);
  }

  @Override
  public boolean replay(Collection<Node<SagaRequest>> nodes, Map<String, SagaResponse> completedOperations, Collection<SagaResponse> responses) {

    for (Iterator<Node<SagaRequest>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      SagaRequest request = iterator.next().value();
      if (completedOperations.containsKey(request.id())) {
        log.info("Skipped completed compensation id={} operation={} while replay", request.id(), request.transaction());
        iterator.remove();
      } else if (!completedTransactions.containsKey(request.id())) {
        // this transaction never started
        log.info("Skipped pending transaction id={} operation={} while replay", request.id(), request.transaction());
        iterator.remove();
      }
    }
    return !nodes.isEmpty();
  }

  private SagaResponse responseOf(List<SagaResponse> responses) {
    if (responses.size() == 1) {
      return responses.get(0);
    }
    return new CompositeSagaResponse(responses);
  }
}
