from __future__ import annotations


def code_outside_literals_and_comments(sql: str) -> str:
    """Mask SQL literals/comments while preserving executable token positions."""
    chars = list(sql)
    index = 0
    state = "code"
    quote = ""
    while index < len(chars):
        current = chars[index]
        following = chars[index + 1] if index + 1 < len(chars) else ""
        if state == "code":
            if current in ("'", '"', "`"):
                state, quote, chars[index] = "quoted", current, " "
            elif current == "#":
                state, chars[index] = "line_comment", " "
            elif (current == "-" and following == "-"
                    and (index + 2 == len(chars) or chars[index + 2].isspace())):
                state, chars[index], chars[index + 1] = "line_comment", " ", " "
                index += 1
            elif current == "/" and following == "*":
                state, chars[index], chars[index + 1] = "block_comment", " ", " "
                index += 1
        elif state == "quoted":
            chars[index] = "\n" if current == "\n" else " "
            if current == "\\":
                if index + 1 < len(chars):
                    index += 1
                    chars[index] = "\n" if chars[index] == "\n" else " "
            elif current == quote:
                if following == quote:
                    index += 1
                    chars[index] = " "
                else:
                    state, quote = "code", ""
        elif state == "line_comment":
            chars[index] = "\n" if current == "\n" else " "
            if current == "\n":
                state = "code"
        else:
            chars[index] = "\n" if current == "\n" else " "
            if current == "*" and following == "/":
                index += 1
                chars[index] = " "
                state = "code"
        index += 1
    return "".join(chars)
