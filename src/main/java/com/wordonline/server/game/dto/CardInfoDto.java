package com.wordonline.server.game.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// This class is used to send card information to the client. Each entry is one magics.id.
public class CardInfoDto {
    private final List<Long> added;

    public CardInfoDto() {added = new ArrayList<>();}
    public CardInfoDto(List<Long> added) {this.added = added;}

    public void addCard(long magicId) {
        added.add(magicId);
    }
}
