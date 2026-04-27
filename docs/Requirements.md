Project Brief: Price Drop Monitor for Amazon Products

Target effort: 2 to 4 hours of AI-assisted coding work. If you find yourself going well past 4 hours of coding (think 8 hours or more), stop and write about the tradeoff you made to get a completion point.

Objective

Build an application that monitors the price of a small set of Amazon products and sends an automated notification when a price drop is detected. We are less interested in whether every feature is polished and more interested in how you design the system, how you handle things that go wrong, and the tradeoffs you made along the way.

Language and tooling

Our production stack is Java, but this project is not a language test. Use whatever language lets you produce the strongest technical solution in the time you have. Java is preferred if you are comfortable there, but we would rather see a sharp solution in a language you know well than a rough one in Java. Tell us in your design doc why you picked what you picked.

AI-assisted development is expected. Use Claude Code, Cursor, Copilot, or whatever you normally use. We care about your decisions and what you verified, not the line count.

Core requirements

You must deliver all of the following. Within each requirement there is deliberate room to make your own choices; those choices are what we will discuss in the panel.

1. Monitor multiple products

Track at least 3 Amazon product URLs concurrently. The set of products should be configurable (configuration file, CLI argument, small UI, whatever you prefer). Adding or removing a product should not require a code change.

2. Periodic price checks

Check prices on a regular interval. The check frequency should be configurable. Pick a scheduling approach and be ready to defend it.

3. Durable price history

Persist every price check so that the history survives a process restart. Pick your own storage layer and schema. You will be asked in the panel why you chose what you chose and what you would change at 10x scale.

4. Price-drop detection and notification

Compare the current price to the last recorded price and send a notification when a drop is detected. Notification method is your choice: email, SMS, Slack webhook, desktop toast, a web UI banner, anything that a reviewer can verify works. The threshold that counts as a meaningful drop should be configurable (absolute value, percentage, or both, your call).

5. Price history visualization

Produce a view of price history over time per product. A simple web page, a static chart, or a dashboard endpoint are all acceptable. The visualization should be usable, not necessarily beautiful.

6. Configurable parameters

At minimum, these should be configurable without a code change: product list, check interval, notification threshold, notification method. Choose a configuration approach that scales reasonably.

7. Logging and observability

Every price check and every notification event should be captured with enough detail that someone else could debug the system from the logs alone. Structured logs are encouraged. Think about what you would want to see at 2 AM if this thing was misbehaving.

8. Failure handling

The real world is messy. Things will go wrong during scraping, networking, and notification delivery. Your system should keep running when individual checks fail, and you should be able to tell us what you did and did not handle, and why.

9. Tests

At least one meaningful test per layer (scraping, storage, comparison logic, notification). You do not need full coverage; you do need tests that would catch a real regression in the logic that matters.

Deliverables

·       A link to a GitHub repository with the source code and a clear commit history.

·       A README covering how to install, configure, and run the application, plus how to verify it works end to end.

·       A 1-page design document (in the repo) that names at least three real tradeoffs you considered and why you chose the path you chose. Storage choice, scheduling approach, and notification strategy are fair game; so is anything else you found interesting.

·       An AI-NOTES.md (or a section in the README) describing one thing your AI assistant got wrong or tried to oversimplify during this build, and how you caught it and fixed it. Short is fine; honest is better.

How we will evaluate

·       Functionality: does the application actually run end to end and produce correct notifications on a real price drop?

·       Design clarity: can you articulate the tradeoffs in your 1-page design doc and defend them in conversation?

·       Failure handling: what happens when something goes wrong, and what did you choose not to handle and why?

·       Data model: is the history durably stored with a schema that makes sense?

·       Separation of concerns: is scraping, storage, comparison, and notification cleanly decoupled, or is it all one file?

·       AI collaboration: does your AI-NOTES entry show real judgment about where your tools helped and where they misled?

·       Readability: can a reviewer understand the code and the design without running it first?

Legal and ethical considerations

·       Ensure your solution adheres to Amazon's terms of service regarding web scraping.

·       Do not use or distribute personal data without proper authorization and consent.

·       If you use a paid API or LLM as part of the solution, keep your API keys out of the repository.

What the panel conversation will look like

We are not looking for perfection. We are looking for engineers who can think clearly about tradeoffs. Come prepared to walk through:

·       Your design doc and the three tradeoffs you named.

·       One place where your solution could break that you knew about and left alone, and why.

·       One place where you learned something unexpected while building this.

·       Your AI-NOTES entry, in detail.

There are no gotcha questions and no whiteboard coding. The goal is to understand how you think.

Stretch goals (optional)

Pick any that interest you. Do not attempt all of them inside the 2-4 hour window; prioritize the core requirements first. Stretch work is a bonus, not a requirement, and well-executed core requirements beat half-done stretch goals every time.

1.     Multi-source comparison. Add a second source (a different retailer or a public product API) and show cross-site price comparison for the same product.

2.     Alert rules engine. Instead of a single threshold, let a user define rules like 'notify if drop is more than 10 percent AND the price is below $50.' Describe how you represent rules.

3.     Deployability. Containerize the application and provide a docker-compose that spins up the app plus its datastore. Bonus points for a GitHub Actions workflow that runs the tests on push.

4.     Live-updating dashboard. Replace the static chart with a dashboard that updates in real time as new data lands. Justify the delivery mechanism.

5.     Cost and rate-limit awareness. If any part of your solution uses paid resources, track usage per check cycle and surface it. Explain what would keep it cheap at 10x the product count.

6.     Concurrency correctness. If two workers run at the same time, or the process restarts mid-check, how do you prevent duplicate notifications? Implement and describe.

7.     REST export. Expose a minimal endpoint that returns price history as JSON for a given product. Document the contract as if another team would consume it.

8.     AI-assisted change summary. When sending a notification, include an AI-generated summary of what has changed in the price over the last 7 days. Describe how you kept the summary accurate.

9.     Self-healing behavior. If your scraper breaks because the page changes, what happens? Design an approach that would degrade gracefully and describe it.

Closing note

The project is deliberately open-ended. Expect to run into situations where there is no obvious right answer; those are the moments we most want to hear about in the panel. Make a call, document why, and move on. That is what the job actually looks like.