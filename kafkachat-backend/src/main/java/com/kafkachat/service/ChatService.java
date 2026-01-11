@Transactional
public ChatDTO createOrGetPrivateChat(Long user1Id, Long user2Id) {

    return chatRepository
            .findPrivateChatBetweenUsers(user1Id, user2Id)
            .stream()
            .findFirst()
            .map(this::toDto)
            .orElseGet(() -> {

                User user1 = userRepository.findById(user1Id)
                        .orElseThrow(() -> new NoSuchElementException("User not found"));

                User user2 = userRepository.findById(user2Id)
                        .orElseThrow(() -> new NoSuchElementException("User not found"));

                Chat chat = Chat.builder()
                        .chatType(Chat.ChatType.PRIVATE)
                        .creator(user1)
                        .build();

                chat.addParticipant(user1);
                chat.addParticipant(user2);

                return toDto(chatRepository.save(chat));
            });
}
