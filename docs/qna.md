# Q&A

---

**Q. How did you handle scenarios where the price format or page structure might change unexpectedly?**

I currently log errors when the page structure or price format changes. In future improvements, I'd implement a notification system that alerts developers if a major structural change is detected. This ensures we can respond quickly and adapt the scraper as needed.

**Q. How frequently does your system check for price changes, and did you consider any rate-limiting?**

Currently, the system checks prices once every hour using a scheduled task, and this frequency is configurable. I did consider rate-limiting; I ensure we stay within Amazon’s acceptable request thresholds to avoid any disruptions or blocking.

**Q. Is there any specific scenario where you think the system could break, and how would you handle it?**

(1) Amazon bot detection / CAPTCHA / Change of price format or page structure

Yes, the system could break if Amazon changes its page structure or price format unexpectedly. In that case, the scraper would fail to extract the price, and the system would log an error and move on to the next product. For future improvements, I'd implement a notification system that alerts developers if a major structural change is detected.

(2) No event reprocessing queue

I don’t have an event queue, so if scraping fails, it just retries at the next scheduled run—no outbox is needed now. But notification failures are a known gap. If Slack is down right when a price drops, the notification is lost. At a production scale, I’d add an outbox table to ensure guaranteed delivery.”


**Q. How would you handle multiple concurrent users requesting price checks simultaneously?**

For multiple users, I’d introduce a task queue system. Requests would be queued and processed by scalable worker instances. This way, we can handle concurrent price checks efficiently, ensuring responsiveness even with multiple users.