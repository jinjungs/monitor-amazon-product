# Q&A

---

**Q. How did you handle scenarios where the price format or page structure might change unexpectedly?**

I currently log errors when the page structure or price format changes. In future improvements, I'd implement a notification system that alerts developers if a major structural change is detected. This ensures we can respond quickly and adapt the scraper as needed.

**Q. How frequently does your system check for price changes, and did you consider any rate-limiting?**

Currently, the system checks prices once every hour using a scheduled task, and this frequency is configurable. I did consider rate-limiting; I ensure we stay within Amazon’s acceptable request thresholds to avoid any disruptions or blocking.


