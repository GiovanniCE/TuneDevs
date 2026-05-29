# Research Report
## Database Schema Optimization
### Summary of Work
My research included reviewing lecture materials from CS564 which described BCNF and Chase's Algorithm. I then ran these tests by hand to verify that the database was optimized for redundancy and strict primary-keys.
### Motivation
This research was very important because databases are a primary volunerability to security and efficiency. Because our program includes login info, it is especially important to map out a secure database to make sure that people's information is kept confidential. 
### Time Spent
I spent 30 minutes refreshing myself with the content and how to perform the mathematics to be able to check for BCNF and Chase's Algorithm. After this, I spent 1h30min checking by hand each of the relations, listiing out all functional dependencies, and making sure that there was no redundancy.
### Results
Taking the CS564 (Databases) class has taught me the importance of having an efficient schema with the users security being a primary concern. Applying what I have learned to this current database will make the queries we run on it much more efficient, be more lightweight since we have omitted redundancy, and more secure for the end user.
### Sources
<!--list your sources and link them to a footnote with the source url-->
- CS564 Lecture Materials [^1]

[^1]: CS564 Lecture Materials: Lecture 8