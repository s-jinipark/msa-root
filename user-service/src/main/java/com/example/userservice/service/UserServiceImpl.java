package com.example.userservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.userservice.client.OrderServiceClient;
import com.example.userservice.dto.UserDto;
import com.example.userservice.jpa.UserEntity;
import com.example.userservice.jpa.UserRepository;
import com.example.userservice.vo.ResponseOrder;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    Environment env;

    UserRepository userRepository;

    BCryptPasswordEncoder passwordEncoder;

    RestTemplate restTemplate;

    OrderServiceClient orderServiceClient;
    
    CircuitBreakerFactory circuitBreakerFactory;

    public UserServiceImpl(Environment env, UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder, RestTemplate restTemplate,
                           OrderServiceClient orderServiceClient,
                           CircuitBreakerFactory circuitBreakerFactory) {
        this.env = env;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
        this.orderServiceClient = orderServiceClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public UserDto createUser(UserDto userDto) {
        userDto.setUserId(UUID.randomUUID().toString());

        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        UserEntity userEntity = mapper.map(userDto, UserEntity.class);
//        userEntity.setEncryptedPwd("encrypted_password");
        userEntity.setEncryptedPwd(passwordEncoder.encode(userDto.getPwd()));

        userRepository.save(userEntity);

        UserDto returnUserDto = mapper.map(userEntity, UserDto.class);

        return returnUserDto;
    }

    @Override
    public UserDto getUserByUserId(String userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if (userEntity == null)
            throw new UsernameNotFoundException("User not found");

        UserDto userDto = new ModelMapper().map(userEntity, UserDto.class);

        //List<ResponseOrder> orderList = new ArrayList<>();
        //String orderUrl = String.format(env.getProperty("order-service.url"), userId);
        //String url = "http://127.0.0.1:8000/order-service/%s/orders";  //-> 오류남
        //String url = "http://localhost:8000/order-service/%s/orders";  //=> OK
        
        /*
        String url = "http://order-service/order-service/%s/orders";
        String orderUrl = String.format(url, userId);
        ResponseEntity<List<ResponseOrder>> orderListResponse =
                restTemplate.exchange(orderUrl, HttpMethod.GET, null,
                                            new ParameterizedTypeReference<List<ResponseOrder>>() {
                });
        List<ResponseOrder> orderList = orderListResponse.getBody();
        */

        /* using a feignclient */
        //List<ResponseOrder> orderList = orderServiceClient.getOrders(userId);

        /* circuitbreaker */
        List<ResponseOrder> orderList = new ArrayList<>();
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("circuitBreaker1");
        //CircuitBreaker circuitBreaker = circuitBreakerFactory.create("default");
        orderList = circuitBreaker.run(() -> orderServiceClient.getOrders(userId),
              throwable -> new ArrayList<>());
        
        userDto.setOrders(orderList);

        return userDto;
    }

    @Override
    public Iterable<UserEntity> getUserByAll() {
        return userRepository.findAll();
    }
}
