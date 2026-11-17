-- Seed data for Marquee.
--
-- Usage (after the database schema has been initialized):
--   sqlite3 path/to/marquee.db < src/main/resources/seed.sql
--
-- Contains:
--   15 films
--   10 genres
--   38 film-genre associations
--   15 screenings
--
-- The script is idempotent:
--   Existing records are preserved.
--   Missing records are inserted.
--   Running the script repeatedly does not create duplicates.
--
-- Designed to test filtering, relationships, and pagination
-- with a default page size of 10 records.

-- --------------------------------------------------
-- Genres
-- --------------------------------------------------

INSERT OR IGNORE INTO genres (genre_id, name) VALUES
    (1, 'Action'),
    (2, 'Adventure'),
    (3, 'Animation'),
    (4, 'Comedy'),
    (5, 'Drama'),
    (6, 'Horror'),
    (7, 'Science Fiction'),
    (8, 'Thriller'),
    (9, 'Documentary'),
    (10, 'Fantasy');


-- --------------------------------------------------
-- Films
-- --------------------------------------------------

INSERT OR IGNORE INTO films (film_id, title, year, description) VALUES
    (1, 'The Invisible Fight', 2023,
    'A heavy metal fan in Soviet Estonia encounters monks who practice kung fu.'),

    (2, '2001: A Space Odyssey', 1968,
    'An exploration of human evolution, artificial intelligence and space exploration.'),

    (3, 'Spirited Away', 2001,
    'A young girl enters a mysterious spirit world and must find a way home.'),

    (4, 'The Dark Knight', 2008,
    'Batman faces the Joker, a criminal mastermind determined to create chaos in Gotham.'),

    (5, 'The Grand Budapest Hotel', 2014,
    'A legendary concierge and his lobby boy become involved in a murder mystery.'),

    (6, 'Parasite', 2019,
    'A poor family gradually infiltrates the household of a wealthy family.'),

    (7, 'Alien', 1979,
    'A commercial spaceship crew encounters a deadly extraterrestrial organism.'),

    (8, 'Mad Max: Fury Road', 2015,
    'A road warrior joins a rebellion against a tyrannical ruler in a post-apocalyptic wasteland.'),

    (9, 'The Godfather', 1972,
    'The aging patriarch of a crime family transfers control to his reluctant son.'),

    (10, 'Get Out', 2017,
    'A young man uncovers disturbing secrets during a visit to his girlfriend''s family.'),

    (11, 'Interstellar', 2014,
    'A group of explorers travels through a wormhole in search of a future for humanity.'),

    (12, 'Coco', 2017,
    'A young aspiring musician journeys into the Land of the Dead to discover his family history.'),

    (13, 'The Lord of the Rings: The Fellowship of the Ring', 2001,
    'A hobbit begins a dangerous journey to destroy a powerful and ancient ring.'),

    (14, 'The Social Dilemma', 2020,
    'A documentary examining the influence of social media and technology on society.'),

    (15, 'Knives Out', 2019,
    'A detective investigates the mysterious death of a wealthy crime novelist.');


-- --------------------------------------------------
-- Film-genre associations
-- --------------------------------------------------

INSERT OR IGNORE INTO film_genres (film_id, genre_id) VALUES
    (1, 1), (1, 4), (1, 5),
    (2, 7), (2, 5),
    (3, 3), (3, 10), (3, 2),
    (4, 1), (4, 8), (4, 5),
    (5, 4), (5, 5),
    (6, 5), (6, 8), (6, 4),
    (7, 6), (7, 7), (7, 8),
    (8, 1), (8, 2), (8, 7),
    (9, 5), (9, 8),
    (10, 6), (10, 8),
    (11, 7), (11, 5), (11, 2),
    (12, 3), (12, 4), (12, 10),
    (13, 2), (13, 10), (13, 1),
    (14, 9),
    (15, 4), (15, 8);


-- --------------------------------------------------
-- Screenings
-- --------------------------------------------------

INSERT OR IGNORE INTO screenings
(screening_id, film_id, venue, screening_time)
VALUES
    (1, 1, 'Main Hall', '2025-10-03T19:30:00Z'),
    (2, 2, 'Open Air Cinema', '2025-10-04T18:00:00Z'),
    (3, 3, 'Studio Theatre', '2025-10-05T16:00:00Z'),
    (4, 4, 'Main Hall', '2025-10-10T20:00:00Z'),
    (5, 5, 'Riverside Cinema', '2025-10-11T17:30:00Z'),
    (6, 6, 'Studio Theatre', '2025-10-12T19:00:00Z'),
    (7, 7, 'Main Hall', '2025-11-01T20:30:00Z'),
    (8, 8, 'Open Air Cinema', '2025-11-02T18:30:00Z'),
    (9, 9, 'Riverside Cinema', '2025-11-07T19:00:00Z'),
    (10, 10, 'Studio Theatre', '2025-11-08T20:00:00Z'),
    (11, 11, 'Main Hall', '2025-11-14T19:30:00Z'),
    (12, 12, 'Open Air Cinema', '2025-11-15T17:00:00Z'),
    (13, 13, 'Riverside Cinema', '2025-11-21T18:00:00Z'),
    (14, 14, 'Studio Theatre', '2025-11-22T16:30:00Z'),
    (15, 15, 'Main Hall', '2025-12-05T19:00:00Z');
