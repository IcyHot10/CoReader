# Implementation Plan - AI Recommendation Swipe Popup

Enhance the AI recommendation experience by introducing a swipable popup where users can review book suggestions one by one, with spoiler-free descriptions, and choose to add or reject them.

## User Review Required

> [!IMPORTANT]
> - The AI will now be asked to provide a "spoiler-free description" for each book.
> - Suggestions will no longer be automatically added to the Firestore `readingLists` collection. Instead, they will be held in a temporary local list for review.
> - Swiping **Right** will add the book to your Reading List.
> - Swiping **Left** will dismiss the recommendation.

## Proposed Changes

### Data Model

#### [MODIFY] [ReadingListBook.kt](file:///C:/Users/Abdul/AndroidStudioProjects/CoReader/app/src/main/java/com/indeavour/coreader/model/firebase/ReadingListBook.kt)
- Add `val description: String = ""` to the `ReadingListBook` data class.

### ViewModel

#### [MODIFY] [ReadingListViewModel.kt](file:///C:/Users/Abdul/AndroidStudioProjects/CoReader/app/src/main/java/com/indeavour/coreader/viewmodel/ReadingListViewModel.kt)
- **Prompt Update**: Update the Gemini prompt to request a JSON array of objects with `title`, `author`, and `description` (spoiler-free).
- **Pending Suggestions**:
    - Add `_pendingSuggestions` `MutableStateFlow<List<ReadingListBook>>`.
    - Expose `pendingSuggestions` as a `StateFlow`.
- **Logic Refactor**:
    - Update `generateAISuggestions` to update `_pendingSuggestions` instead of Firestore.
    - Add `acceptSuggestion(suggestion: ReadingListBook)`: Saves the book to Firestore as part of the user's reading list.
    - Add `dismissSuggestion(suggestionId: String)`: Removes the suggestion from the local `pendingSuggestions` list.

### UI

#### [MODIFY] [ReadingListScreen.kt](file:///C:/Users/Abdul/AndroidStudioProjects/CoReader/app/src/main/java/com/indeavour/coreader/screen/ReadingListScreen.kt)
- **Swipe Overlay**:
    - Create a new UI component that displays a card when `pendingSuggestions` is not empty.
    - Implement swipe-to-dismiss logic (Left for Reject, Right for Add).
    - Display the new `description` field prominently on the card.
- **Animations**: Add smooth transitions as cards are swiped and the next recommendation appears.

## Verification Plan

### Automated Tests
- Verify that the AI prompt correctly requests all three fields.
- Verify that the JSON parser correctly extracts the `description` field.

### Manual Verification
1. Open the Reading List screen.
2. Tap the **AI Sparkle** or use the **Chat**.
3. Verify that a popup appears showing the first recommendation with its description.
4. Swipe **Right** and verify the book is added to "My Reading List" (persisted in Firestore).
5. Swipe **Left** and verify the recommendation is dismissed.
6. Verify the popup disappears once all recommendations are reviewed.
