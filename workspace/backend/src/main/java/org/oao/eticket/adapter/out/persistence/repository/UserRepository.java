package org.oao.eticket.adapter.out.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.oao.eticket.adapter.out.persistence.entity.UserJpaEntity;
import org.oao.eticket.adapter.out.persistence.mapper.UserMapper;
import org.oao.eticket.application.domain.model.User;
import org.oao.eticket.application.port.out.CreateUserCommand;
import org.oao.eticket.application.port.out.CreateUserPort;
import org.oao.eticket.application.port.out.LoadUserPort;
import org.oao.eticket.common.annotation.PersistenceAdapter;
import org.oao.eticket.exception.ExternalServiceException;
import org.oao.eticket.exception.UserDuplicateException;
import org.oao.eticket.exception.UserNotFoundException;
import org.oao.eticket.exception.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

@PersistenceAdapter
@RequiredArgsConstructor
class UserRepository implements CreateUserPort, LoadUserPort {

  private final UserMapper userMapper;

  @PersistenceContext private final EntityManager entityManager;

  @Transactional
  public User create(final CreateUserCommand cmd) {
    try {
      final var user = userMapper.mapToJpaEntity(cmd);
      entityManager.persist(user);
      return userMapper.mapToDomainEntity(user);
    } catch (ConstraintViolationException e) {
      if (e.getMessage().contains("Duplicate entry")) {
        throw new UserDuplicateException(e);
      }
      throw e;
    }
  }

  @Override
  public User loadById(final User.UserId id) {
    try {
      final var user =
          entityManager
              .createQuery(
                  """
                            SELECT u
                            FROM UserJpaEntity u
                            WHERE u.id=:id""",
                  UserJpaEntity.class)
              .setParameter("id", id.getValue())
              .getSingleResult();
      return userMapper.mapToDomainEntity(user);
    } catch (Exception e) {
      // TODO(meo-s): must be wrap exception and throw it
      throw e;
    }
  }

  @Override
  public User loadByUsername(final String username) {
    try {
      final var user =
          entityManager
              .createQuery(
                  """
                          SELECT u
                            FROM UserJpaEntity u
                           WHERE u.username=:username""",
                  UserJpaEntity.class)
              .setParameter("username", username)
              .getSingleResult();
      return userMapper.mapToDomainEntity(user);
    } catch (NoResultException e) {
      throw new UsernameNotFoundException(username, e);
    } catch (Exception e) {
      // TODO(meo-s): must be wrap exception and throw it
      throw e;
    }
  }

  @Override
  public User loadByWallet(final byte[] address) {
    try {
      final var user =
          entityManager
              .createQuery(
                  """
                      SELECT u
                        FROM UserJpaEntity u
                       WHERE u.walletAddress=:address""",
                  UserJpaEntity.class)
              .setParameter("address", address)
              .getSingleResult();
      return userMapper.mapToDomainEntity(user);
    } catch (NoResultException e) {
      final var addressInHex = Numeric.toHexString(address);
      throw new UserNotFoundException("Owner of " + addressInHex + " not found", e);
    } catch (Exception e) {
      throw new ExternalServiceException(e);
    }
  }

  @Override
  public User loadByWallet(final String address) {
    return loadByWallet(Numeric.hexStringToByteArray(address));
  }
}
