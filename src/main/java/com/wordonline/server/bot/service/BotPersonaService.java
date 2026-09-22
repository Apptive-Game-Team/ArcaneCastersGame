package com.wordonline.server.bot.service;

import com.wordonline.server.bot.domain.BotParticipant;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.dto.BotPersonaRequestDto;
import com.wordonline.server.bot.repository.BotPersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
public class BotPersonaService {

    private final BotPersonaRepository botPersonaRepository;

    @Transactional(readOnly = true)
    public List<BotPersona> findAll() {
        return botPersonaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BotPersona> findEnabled() {
        return findAll().stream()
                .filter(BotPersona::enabled)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BotPersona> findRandomEnabled() {
        List<BotPersona> enabled = findEnabled();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())));
    }

    @Transactional(readOnly = true)
    public Optional<BotPersona> findByUserId(long userId) {
        return botPersonaRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public BotPersona findOrDefault(long userId) {
        return findByUserId(userId).orElse(BotPersona.DEFAULT);
    }

    @Transactional(readOnly = true)
    public BotPersona findByParticipantIdOrDefault(long participantId) {
        if (!BotParticipant.isBot(participantId)) {
            return BotPersona.DEFAULT;
        }
        return findOrDefault(participantId);
    }

    public BotPersona create(BotPersonaRequestDto requestDto) {
        BotParticipant.requireBotUserId(requestDto.userId());
        return botPersonaRepository.create(normalize(requestDto));
    }

    public BotPersona update(long userId, BotPersonaRequestDto requestDto) {
        BotParticipant.requireBotUserId(userId);
        if (requestDto.userId() != userId) {
            throw new IllegalArgumentException("Bot user ID cannot be changed.");
        }
        if (botPersonaRepository.update(userId, normalize(requestDto)) == 0) {
            throw new IllegalArgumentException("Bot persona not found: " + userId);
        }
        return findByUserId(userId).orElseThrow();
    }

    public void delete(long userId) {
        BotParticipant.requireBotUserId(userId);
        if (botPersonaRepository.delete(userId) == 0) {
            throw new IllegalArgumentException("Bot persona not found: " + userId);
        }
    }

    private BotPersonaRequestDto normalize(BotPersonaRequestDto requestDto) {
        return new BotPersonaRequestDto(
                requestDto.userId(),
                requestDto.name(),
                requestDto.tier(),
                Math.max(0, requestDto.thinkingTimeMs()),
                Math.max(1, requestDto.reactionIntervalFrames()),
                Math.max(-1.0, Math.min(1.0, requestDto.counterAggression())),
                requestDto.enabled(),
                requestDto.hospitality(),
                requestDto.temperament() == null ? BotTemperament.WARM : requestDto.temperament()
        );
    }
}
