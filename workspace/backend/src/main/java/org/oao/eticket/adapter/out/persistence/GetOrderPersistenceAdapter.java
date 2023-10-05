package org.oao.eticket.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.oao.eticket.adapter.out.persistence.repository.TicketingRepository;
import org.oao.eticket.application.port.out.GetOrderPort;
import org.oao.eticket.common.annotation.PersistenceAdapter;

@PersistenceAdapter
@RequiredArgsConstructor
public class GetOrderPersistenceAdapter implements GetOrderPort {

  private final TicketingRepository ticketingRepository;

  @Override
  public Long getOrder(String key, Integer userId) {
    return ticketingRepository.zRank(key, userId);
  }
}
