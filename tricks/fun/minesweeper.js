let UNICODE = [":boom:", ":zero:", ":one:", ":two:", ":three:", ":four:", ":five:", ":six:", ":seven:", ":eight:"]

if (args.length < 2) {
    throw "Invalid Usage! <width> <height>"
}

let spoiler = true
let width = parseInt(args[0])
let height = parseInt(args[1])
let pointCount = 20

if (width < 2 || width > 20 || height < 2 || height > 20) {
    throw "The size of the board must be between 2x2 and 20x20!"
}

let points = []

hasPoint = function (x, y) {
    for (let p of points) {
        if (p.x === x && p.y === y) return true
    }
    return false
}

for (let i = 0; i < pointCount; i++) {
    let x = Math.floor(width * Math.random())
    let y = Math.floor(height * Math.random())
    while (hasPoint(x, y)) {
        x = Math.floor(width * Math.random())
        y = Math.floor(height * Math.random())
    }
    points.push({x: x, y: y})
}

grid = []

for (let i = 0; i < width; i++) {
    let arr = []
    for (j = 0; j < height; j++) {
        arr.push(1)
    }
    grid.push(arr)
}

inc = function (x, y) {
    if (x < 0) return
    if (y < 0) return
    if (x > width - 1) return
    if (y > height - 1) return
    grid[x][y]++
}

for (let point of points) {
    inc(point.x - 1, point.y - 1)
    inc(point.x - 1, point.y)
    inc(point.x - 1, point.y + 1)
    inc(point.x, point.y - 1)
    inc(point.x, point.y + 1)
    inc(point.x + 1, point.y - 1)
    inc(point.x + 1, point.y)
    inc(point.x + 1, point.y + 1)
}

for (let point of points) {
    grid[point.x][point.y] = 0
}

formatRow = function (row) {
    let formatted = ""
    let sp = spoiler ? "||" : ""
    for (let number of row) {
        formatted = formatted + sp + UNICODE[number] + sp
    }
    return formatted
}

let result = ""
for (let row of grid) {
    result = result + formatRow(row) + "\n"
}
channel.sendMessage(result)
