package org.oao.eticket.adapter.out.persistence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.oao.eticket.application.domain.model.PerformanceScheduleSeatTable;
import org.oao.eticket.application.domain.model.Seat;
import org.oao.eticket.application.domain.model.SeatStatus;
import org.oao.eticket.application.port.out.SaveVacanciesRedisPort;
import org.oao.eticket.application.port.out.dto.PreemptVacancyCommand;
import org.oao.eticket.application.port.out.PreemptVacancyPort;
import org.oao.eticket.application.port.out.dto.LoadVacanciesRedisCommand;
import org.oao.eticket.application.port.out.LoadVacanciesRedisPort;
import org.oao.eticket.common.annotation.PersistenceAdapter;
import org.oao.eticket.exception.NoResultException;
import org.oao.eticket.exception.PreemptVacancyFailureException;
import org.oao.eticket.exception.UnexpectedException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
public class VacanciesRedisRepository
    implements SaveVacanciesRedisPort, LoadVacanciesRedisPort, PreemptVacancyPort {

  private final RedisTemplate<String, Object> redisTemplate;
  private final HashOperations<String, String, PerformanceScheduleSeatTable> hashOperations;
  private static final String TABLE_KEY = "SeatTable";

  // PerformanceScheduleSeatTable 객체 저장
  @Override
  public void saveTable(PerformanceScheduleSeatTable table) {
    String key = getKey(table);
    if (hashOperations.hasKey(TABLE_KEY, key)) {
      System.out.println(TABLE_KEY + "에 " + key + "는 이미 들어 있다.");
      //      throw new SeatTableDuplicatedException(String.format("%s에 대한 좌석 정보가 이미 존재 합니다.",
      // key));
    } else {
      hashOperations.put(TABLE_KEY, key, table);
    }
  }

  // 좌석 정보 조회
  @Override
  public List<Seat> getSeatTable(LoadVacanciesRedisCommand cmd) {
    PerformanceScheduleSeatTable table =
        getTable(cmd.getPerformanceScheduleId(), cmd.getSectionId());
    if (table != null) {
      return table.getSeats();
    } else {
      throw new NoResultException("좌석 없음");
    }
  }

  // 특정 좌석의 status 업데이트
  @Override
  public PerformanceScheduleSeatTable preemptVacancy(PreemptVacancyCommand cmd) {
    PerformanceScheduleSeatTable table =
        getTable(cmd.getPerformanceScheduleId(), cmd.getSectionId());
    System.out.println(cmd.getSectionId());
    if (table != null) {
      List<Seat> seats = table.getSeats();
      for (Seat seat : seats) {
        if (seat.getId().equals(cmd.getSeatId())) {
          if (seat.getSeatStatus() != SeatStatus.ONSALE) {
            throw new PreemptVacancyFailureException(cmd.getSeatId().toString());
          }
          seat.setSeatStatus(SeatStatus.PREEMPTED);
          table.setSeats(seats);
          hashOperations.put(TABLE_KEY, getKey(table), table);
          System.out.println("hello");
          return table;
        }
      }
    }
    throw new UnexpectedException("id가 잘못 됨. 일치하는 좌석 없음.");
  }

  // PerformanceScheduleSeatTable 객체 조회
  public PerformanceScheduleSeatTable getTable(Integer performanceScheduleId, Integer sectionId) {
    String key = getKey(performanceScheduleId, sectionId);

    if (hashOperations.hasKey(TABLE_KEY, key)) {
      return hashOperations.get(TABLE_KEY, key);
    } else { // 해당 키가 Redis에 존재하지 않을 때 처리
      throw new UnexpectedException("performance Schedule Id나 section Id 틀림. 그런 데이터 없음");
    }
  }

  // Redis에 저장할 키 생성
  private String getKey(PerformanceScheduleSeatTable table) {
    return getKey(table.getPerformanceScheduleId(), table.getSectionId());
  }

  private String getKey(Integer performanceScheduleId, Integer sectionId) {
    return performanceScheduleId + ":" + sectionId;
  }
}
